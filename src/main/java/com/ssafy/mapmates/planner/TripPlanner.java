package com.ssafy.mapmates.planner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.planner.ExecutionPlan.ToolStep;
import com.ssafy.mapmates.tool.AttractionTool;
import com.ssafy.mapmates.tool.AttractionTool.Place;
import com.ssafy.mapmates.tool.CultureTool;
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
            ToolStep.GET_WEATHER, ToolStep.OPTIMIZE_ROUTE, ToolStep.SEARCH_PERFORMANCES, ToolStep.SEARCH_DOCUMENTS);

    private final ChatClient chatClient;
    private final AttractionTool attractionTool;
    private final WeatherTool weatherTool;
    private final RouteTool routeTool;
    private final PlanTool planTool;
    private final DocumentTool documentTool;
    private final CultureTool cultureTool;

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
        int days = raw.days() == null ? 1 : Math.max(1, Math.min(raw.days(), 5));
        // 장소 수: 사용자가 정하지 않았으면 하루 4곳 기준
        int count = raw.count() == null ? Math.min(8, days * 4) : Math.max(2, Math.min(raw.count(), 10));
        String docQuery = raw.documentQuery() == null || raw.documentQuery().isBlank() ? question : raw.documentQuery();
        List<String> types = raw.contentTypes() == null ? List.of()
                : raw.contentTypes().stream().filter(t -> t != null && !t.isBlank()).distinct().toList();
        String intent = raw.intent() == null || raw.intent().isBlank() ? question : raw.intent();
        return new ExecutionPlan(steps, nvl(raw.region()), nvl(raw.keyword()), nvl(raw.contentType()), count, docQuery, types, days, intent);
    }

    public List<StepResult> execute(ExecutionPlan plan, Integer memberId) {
        List<StepResult> results = new ArrayList<>();
        List<Place> places = List.of();
        for (ToolStep step : plan.steps()) {
            try {
                switch (step) {
                    case GET_MY_PLANS -> results.add(new StepResult(step.name(), "memberId=" + memberId, planTool.myPlans(memberId)));
                    case SEARCH_ATTRACTIONS -> {
                        List<String> types = plan.contentTypes().isEmpty() && !plan.contentType().isBlank()
                                ? List.of(plan.contentType()) : plan.contentTypes();
                        double radius = plan.days() <= 1 ? 5 : 12;

                        // 1) AI 가 조건에 맞는 장소를 직접 추천한다
                        List<String> suggested = suggestPlaceNames(plan, types);
                        // 2) 추천된 장소가 실제 DB 에 있는지 하나씩 확인한다 (없는 장소는 버린다)
                        Map<Integer, Place> verified = new java.util.LinkedHashMap<>();
                        for (String name : suggested) {
                            if (verified.size() >= plan.count()) break;
                            Place p = attractionTool.findPlaceByName(plan.region(), name);
                            if (p != null) verified.putIfAbsent(p.no(), p);
                        }
                        List<Place> chosen = new ArrayList<>(verified.values());
                        int fromAi = chosen.size();
                        // 3) 확인된 장소가 부족하면, 확인된 장소 주변(없으면 지역 내)의 실제 장소 중에서 AI 가 조건에 맞게 골라 채운다
                        if (chosen.size() < plan.count()) {
                            List<Place> pool = chosen.isEmpty()
                                    ? attractionTool.findCourseCandidates(plan.region(), types, plan.keyword(), radius)
                                    : attractionTool.candidatesNear(chosen.get(0), plan.region(), types, radius);
                            if (pool.isEmpty()) pool = attractionTool.findCourseCandidates(plan.region(), types, "", radius);
                            List<Place> rest = pool.stream().filter(p -> !verified.containsKey(p.no())).toList();
                            chosen.addAll(selectPlaces(rest, plan, plan.count() - chosen.size()));
                        }
                        places = chosen;
                        results.add(new StepResult(step.name(), "region=%s, 조건=%s, AI 추천 %d곳 중 등록 확인 %d곳, 주변에서 보충 %d곳"
                                .formatted(plan.region(), plan.intent(), suggested.size(), fromAi, places.size() - fromAi), places));
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
                    case SEARCH_PERFORMANCES -> results.add(new StepResult(step.name(), "region=" + plan.region(),
                            cultureTool.searchPerformances(plan.region(), "")));
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

    /** AI 가 추천한 장소 이름 목록 */
    record Suggestion(List<String> names) {}

    /** AI 에게 조건에 맞는 실제 장소를 추천받는다. 여유 있게 받아 DB 확인에서 탈락하는 경우에 대비한다. */
    List<String> suggestPlaceNames(ExecutionPlan plan, List<String> types) {
        try {
            String system = "너는 한국 여행 전문가다. 사용자의 조건에 가장 잘 맞는 실제 장소를 추천한다. "
                    + "지정된 지역 안에 실제로 있는 장소만, 서로 이동하기 가까운 곳 위주로 고른다. "
                    + "이름은 지도에서 검색되는 짧은 공식 명칭으로 쓴다(예: 경복궁, 남산서울타워, 광장시장). 설명 없이 이름만 반환한다. "
                    + "매번 뻔한 곳만 고르지 말고 조건에 어울리는 다양한 곳을 섞는다.";
            String user = "지역: " + (plan.region().isBlank() ? "전국" : plan.region())
                    + (plan.keyword().isBlank() ? "" : "\n동네/키워드: " + plan.keyword())
                    + "\n조건: " + plan.intent() + "\n일수: " + plan.days() + "일"
                    + "\n원하는 유형: " + (types.isEmpty() ? "관광지, 문화시설, 음식점" : String.join(", ", types))
                    + "\n추천할 개수: " + (plan.count() * 2 + 2);
            Suggestion s = chatClient.prompt().system(system).user(user).call().entity(Suggestion.class);
            if (s != null && s.names() != null) {
                return s.names().stream().filter(n -> n != null && !n.isBlank()).map(String::trim).distinct().limit(24).toList();
            }
        } catch (Exception e) {
            log.warn("AI 장소 추천 실패: {}", e.getClass().getSimpleName());
        }
        return List.of();
    }

    /** 후보 번호 선택 결과 */
    record Selection(List<Integer> nos) {}

    /**
     * 후보 목록(실제 DB 데이터) 안에서만 고르게 해, 조건은 반영하되 없는 장소를 지어내지 못하게 한다.
     * LLM 응답이 잘못되면 후보 앞에서부터 필요한 수만큼 쓴다.
     */
    List<Place> selectPlaces(List<Place> pool, ExecutionPlan plan, int need) {
        if (need <= 0) return List.of();
        if (pool.size() <= need) return pool;
        try {
            StringBuilder list = new StringBuilder();
            for (Place p : pool) list.append(p.no()).append(" | ").append(p.title()).append(" | ").append(p.type()).append(" | ").append(p.address()).append('\n');
            String system = "너는 여행 코스에 넣을 장소를 고르는 역할이다. 아래 후보 목록에서만 고른다. "
                    + "사용자의 조건(동행, 분위기, 일수)에 어울리는 장소를 정확히 요청한 개수만큼 골라 번호(no)만 반환한다. "
                    + "식사가 어울리는 일정이면 음식점을 하루에 한두 곳 포함하고, 같은 성격의 장소만 고르지 않는다.";
            String user = "조건: " + plan.intent() + "\n일수: " + plan.days() + "일\n고를 개수: " + need
                    + "\n\n[후보: no | 이름 | 유형 | 주소]\n" + list;
            Selection sel = chatClient.prompt().system(system).user(user).call().entity(Selection.class);
            if (sel != null && sel.nos() != null) {
                Map<Integer, Place> byNo = new HashMap<>();
                pool.forEach(p -> byNo.put(p.no(), p));
                List<Place> chosen = sel.nos().stream().distinct().map(byNo::get).filter(p -> p != null).limit(need).toList();
                if (!chosen.isEmpty()) return chosen;
            }
        } catch (Exception e) {
            log.warn("장소 선택 실패, 후보 앞에서부터 사용: {}", e.getClass().getSimpleName());
        }
        return pool.subList(0, need);
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
        if (q.matches(".*(공연|뮤지컬|연극|콘서트|전시).*")) steps.add(ToolStep.SEARCH_PERFORMANCES);
        if (q.matches(".*(문서|가이드|규정|팁|자료).*")) steps.add(ToolStep.SEARCH_DOCUMENTS);
        List<String> types = q.contains("맛집") || q.contains("음식") ? List.of("음식점")
                : q.contains("숙소") || q.contains("숙박") ? List.of("숙박") : List.of("관광지", "문화시설", "음식점");
        int days = q.contains("2박") ? 3 : q.contains("1박") ? 2 : 1;
        return new ExecutionPlan(steps, region, "", "", null, q, types, days, q);
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
