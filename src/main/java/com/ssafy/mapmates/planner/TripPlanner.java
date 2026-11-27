package com.ssafy.mapmates.planner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.planner.ExecutionPlan.ToolStep;
import com.ssafy.mapmates.tool.AttractionTool;
import com.ssafy.mapmates.tool.AttractionTool.Place;
import com.ssafy.mapmates.tool.DocumentTool;
import com.ssafy.mapmates.tool.PlanTool;
import com.ssafy.mapmates.tool.RouteTool;
import com.ssafy.mapmates.tool.WeatherTool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Planner (F1206) - 서버 주도 실행 구조
 * 1) plan    : LLM 에게 "어떤 Tool 이 어떤 순서로 필요한지"만 구조화된 형태로 받는다. (Tool 실행은 하지 않음)
 * 2) normalize: 서버가 계획을 검증한다. 중복 제거, 선행 조건 없는 단계 제거, 고정 순서로 정렬.
 * 3) execute : 서버가 Tool 을 순서대로 직접 호출하고, 앞 단계 결과를 다음 단계 입력으로 넘긴다.
 * LLM 호출(계획·최종 응답)과 Tool 실행(서버 코드)을 분리해 호출 순서와 횟수를 서버가 통제한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripPlanner {
    /** 허용되는 실행 순서. 이 순서를 벗어난 계획은 재정렬된다. */
    private static final List<ToolStep> ORDER = List.of(ToolStep.GET_MY_PLANS, ToolStep.SEARCH_ATTRACTIONS,
            ToolStep.GET_WEATHER, ToolStep.OPTIMIZE_ROUTE, ToolStep.SEARCH_DOCUMENTS);

    private final ChatClient chatClient;
    private final AttractionTool attractionTool;
    private final WeatherTool weatherTool;
    private final RouteTool routeTool;
    private final PlanTool planTool;
    private final DocumentTool documentTool;

    @Value("classpath:/prompts/planner.txt")
    private Resource plannerPrompt;

    public ExecutionPlan plan(String question, boolean loggedIn) {
        ExecutionPlan raw;
        try {
            raw = chatClient.prompt().system(plannerPrompt).user(question).call().entity(ExecutionPlan.class);
        } catch (Exception e) {
            log.warn("LLM 계획 수립 실패, 규칙 기반 계획으로 대체: {}", e.getMessage());
            raw = ruleBasedPlan(question);
        }
        return normalize(raw, question, loggedIn);
    }

    /** 불필요하거나 실행 불가능한 Tool 호출을 제거하고 순서를 고정한다. */
    ExecutionPlan normalize(ExecutionPlan raw, String question, boolean loggedIn) {
        if (raw == null) raw = ruleBasedPlan(question);
        EnumSet<ToolStep> wanted = EnumSet.noneOf(ToolStep.class);
        if (raw.steps() != null) raw.steps().stream().filter(s -> s != null).forEach(wanted::add); // 중복 제거
        if (!wanted.contains(ToolStep.SEARCH_ATTRACTIONS)) { // 선행 결과가 없으면 실행할 수 없는 단계
            wanted.remove(ToolStep.GET_WEATHER);
            wanted.remove(ToolStep.OPTIMIZE_ROUTE);
        }
        if (!loggedIn) wanted.remove(ToolStep.GET_MY_PLANS);
        List<ToolStep> steps = ORDER.stream().filter(wanted::contains).toList();
        int count = raw.count() == null ? 5 : Math.max(3, Math.min(raw.count(), 8));
        String docQuery = raw.documentQuery() == null || raw.documentQuery().isBlank() ? question : raw.documentQuery();
        return new ExecutionPlan(steps, nvl(raw.region()), nvl(raw.keyword()), nvl(raw.contentType()), count, docQuery);
    }

    public List<StepResult> execute(ExecutionPlan plan, Integer memberId) {
        List<StepResult> results = new ArrayList<>();
        List<Place> places = List.of();
        for (ToolStep step : plan.steps()) {
            try {
                switch (step) {
                    case GET_MY_PLANS -> results.add(new StepResult(step.name(), "memberId=" + memberId, planTool.myPlans(memberId)));
                    case SEARCH_ATTRACTIONS -> {
                        places = attractionTool.searchAttractions(plan.region(), plan.keyword(), plan.contentType(), plan.count());
                        if (places.isEmpty() && !plan.keyword().isBlank()) { // 키워드가 너무 좁으면 지역만으로 재검색
                            places = attractionTool.searchAttractions(plan.region(), "", plan.contentType(), plan.count());
                        }
                        results.add(new StepResult(step.name(),
                                "region=%s, keyword=%s, type=%s, count=%d".formatted(plan.region(), plan.keyword(), plan.contentType(), plan.count()), places));
                    }
                    case GET_WEATHER -> {
                        if (places.isEmpty()) continue; // 앞 단계 결과가 없으면 호출하지 않는다
                        Place first = places.get(0);    // 검색 결과의 좌표를 날씨 Tool 입력으로 사용
                        results.add(new StepResult(step.name(), "lat=%.4f, lng=%.4f (%s)".formatted(first.latitude(), first.longitude(), first.title()),
                                weatherTool.getWeather(first.latitude(), first.longitude())));
                    }
                    case OPTIMIZE_ROUTE -> {
                        if (places.size() < 2) continue;
                        List<Integer> nos = places.stream().map(Place::no).toList(); // 검색 결과의 번호를 경로 Tool 입력으로 사용
                        Map<String, Object> route = routeTool.optimizeRoute(nos);
                        results.add(new StepResult(step.name(), "attractionNos=" + nos, route));
                    }
                    case SEARCH_DOCUMENTS -> results.add(new StepResult(step.name(), "query=" + plan.documentQuery(),
                            documentTool.searchDocuments(plan.documentQuery())));
                }
            } catch (Exception e) {
                log.warn("Tool {} 실행 실패: {}", step, e.getMessage());
                results.add(new StepResult(step.name(), "", "실행 실패: " + e.getMessage()));
            }
        }
        return results;
    }

    /** LLM 을 쓸 수 없을 때의 키워드 규칙 기반 계획 */
    ExecutionPlan ruleBasedPlan(String q) {
        List<ToolStep> steps = new ArrayList<>();
        String region = "";
        for (String r : List.of("서울", "인천", "대전", "대구", "광주", "부산", "울산", "세종", "경기", "강원", "충북", "충남", "경북", "경남", "전북", "전남", "제주")) {
            if (q.contains(r)) region = r;
        }
        if (q.contains("내 계획") || q.contains("내 일정") || q.contains("내 여행")) steps.add(ToolStep.GET_MY_PLANS);
        if (!region.isEmpty() || q.matches(".*(추천|코스|가볼|관광|맛집|숙소).*")) steps.add(ToolStep.SEARCH_ATTRACTIONS);
        if (q.matches(".*(날씨|비|기온|우산|옷).*")) steps.add(ToolStep.GET_WEATHER);
        if (q.matches(".*(코스|동선|경로|순서|일정).*")) steps.add(ToolStep.OPTIMIZE_ROUTE);
        if (q.matches(".*(문서|가이드|규정|팁|자료).*")) steps.add(ToolStep.SEARCH_DOCUMENTS);
        String type = q.contains("맛집") || q.contains("음식") ? "음식점" : q.contains("숙소") || q.contains("숙박") ? "숙박" : "";
        return new ExecutionPlan(steps, region, "", type, 5, q);
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
