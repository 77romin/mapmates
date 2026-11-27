package com.ssafy.mapmates.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.planner.CourseBuilder;
import com.ssafy.mapmates.planner.ExecutionPlan;
import com.ssafy.mapmates.planner.StepResult;
import com.ssafy.mapmates.planner.TripPlanner;
import com.ssafy.mapmates.tool.AttractionTool;
import com.ssafy.mapmates.tool.CultureTool;
import com.ssafy.mapmates.tool.DocumentTool;
import com.ssafy.mapmates.tool.PlanTool;
import com.ssafy.mapmates.tool.RouteTool;
import com.ssafy.mapmates.tool.WeatherTool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 응답 생성 (F1203 ~ F1206)
 * - simple  : Tool 없이 LLM 만 호출
 * - single  : Tool 하나만 LLM 에 제공 (단일 Tool 호출 검증)
 * - multi   : 모든 Tool 을 LLM 에 제공, LLM 이 필요한 Tool 을 연쇄 호출 (LLM 주도)
 * - planner : Planner 가 순서를 정하고 서버가 Tool 을 실행, 결과를 Prompt 에 넣어 최종 응답 생성 (서버 주도)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {
    private final ChatClient chatClient;
    private final TripPlanner tripPlanner;
    private final AttractionTool attractionTool;
    private final WeatherTool weatherTool;
    private final RouteTool routeTool;
    private final PlanTool planTool;
    private final DocumentTool documentTool;
    private final CultureTool cultureTool;

    public Map<String, Object> simple(String message) {
        return response("simple", chatClient.prompt().user(message).call().content(), List.of());
    }

    public Map<String, Object> single(String message, String toolName, Integer memberId) {
        Object tool = switch (toolName == null ? "" : toolName) {
            case "attraction" -> attractionTool;
            case "weather" -> weatherTool;
            case "route" -> routeTool;
            case "plan" -> planTool;
            case "document" -> documentTool;
            case "culture" -> cultureTool;
            default -> throw ApiException.badRequest("tool 은 attraction, weather, route, plan, document, culture 중 하나여야 합니다.");
        };
        String answer = chatClient.prompt().user(message).tools(tool)
                .toolContext(Map.of(PlanTool.MEMBER_ID, memberId == null ? 0 : memberId)).call().content();
        return response("single:" + toolName, answer, List.of());
    }

    public Map<String, Object> multi(String message, Integer memberId) {
        String answer = chatClient.prompt().user(message)
                .tools(attractionTool, weatherTool, routeTool, planTool, documentTool, cultureTool)
                .toolContext(Map.of(PlanTool.MEMBER_ID, memberId == null ? 0 : memberId)).call().content();
        return response("multi", answer, List.of());
    }

    public Map<String, Object> planner(String message, Integer memberId, String history) {
        ExecutionPlan plan = tripPlanner.plan(message, memberId != null, history);
        log.debug("실행 계획: {}", plan);
        List<StepResult> results = tripPlanner.execute(plan, memberId, history);

        // Tool 실행 결과(Resource)를 Prompt 에 통합한다. LLM 은 이 데이터만 근거로 답한다.
        StringBuilder context = new StringBuilder();
        for (StepResult r : results) {
            context.append("### ").append(r.tool()).append(" (입력: ").append(r.input()).append(")\n").append(r.output()).append("\n\n");
        }
        // 추천 장소를 일차별 코스로 묶는다. 답변 문장과 화면의 '플랜 만들기'가 같은 배정을 쓴다.
        List<CourseBuilder.Stop> stops = nearbyMeals(course(results, plan.days() == null ? 1 : plan.days()), plan.region());
        if (!stops.isEmpty()) {
            // 이름과 유형만으로는 설명이 뻔해지므로, 등록된 소개 글을 근거로 함께 넘긴다
            Map<Integer, AttractionTool.Brief> briefs = attractionTool.briefs(stops.stream().map(CourseBuilder.Stop::no).toList(), true);
            stops = CourseBuilder.withBriefs(stops, briefs);
            context.append("### 장소 소개 (등록된 소개 글 발췌)\n");
            for (CourseBuilder.Stop st : stops) {
                AttractionTool.Brief b = briefs.get(st.no());
                context.append("- ").append(st.title()).append(": ")
                        .append(b == null || b.overview().isBlank() ? "소개 없음" : AttractionTool.cut(b.overview(), 260)).append("\n");
            }
            context.append("\n");
        }
        if (stops.size() >= 2) {
            context.append("### 일차 배정 (이 배정과 순서를 그대로 따른다)\n").append(CourseBuilder.describe(stops)).append("\n");
        }
        String answer;
        if (results.isEmpty()) {
            answer = chatClient.prompt().user(TripPlanner.withHistory(history, message)).call().content();
        } else {
            // 작성 규칙은 system 메시지로, 근거 데이터와 질문은 user 메시지로 나눠 보낸다.
            // (규칙을 user 메시지에 섞으면 모델이 규칙 문장을 답변에 되풀이하는 경우가 있었다)
            String rules = """
                    너는 여행 플래너 '너랑 갈.지도'의 AI 길잡이다. 서버가 도구를 실행해 얻은 결과를 받아 사용자에게 답한다.
                    - 도구 실행 결과에 있는 사실만 사용한다. 결과에 없는 장소, 주소, 수치, 영업시간은 만들지 않는다.
                    - 비어 있는 결과는 "조회된 정보가 없다"고 알린다.
                    - '일차 배정'이 있으면 그 순서가 최종 방문 순서다. OPTIMIZE_ROUTE 의 order 와 달라도 일차 배정을 따르고, 거리는 일차별 이동 거리로 알린다.
                    - '일차 배정'이 없으면 SEARCH_ATTRACTIONS 의 장소 순서대로 안내한다.
                    - 사용자 조건(%s)에 맞춰 설명하고, 장소마다 왜 이 조건에 어울리는지 한마디를 붙인다. 음식점은 식사 장소로 안내한다.
                    - 그 한마디는 '장소 소개'에 적힌 구체적인 내용(무엇을 볼 수 있는지, 어떤 특징이 있는지, 대표 메뉴 등)을 근거로 쓴다. "둘러보기 좋다", "여행을 시작하기 좋다" 같은 어디에나 붙일 수 있는 말은 쓰지 않는다.
                    - 소개가 없는 장소는 유형과 위치만으로 짧게 안내하고, 내용을 지어내지 않는다.
                    - '일차 배정'에 일차별 이동 거리가 있으면 각 일차 끝에 한 줄로 알린다.
                    - '일차 배정'이 주어지면 그 배정과 순서를 그대로 따라 일차별로 안내한다. 장소를 다른 일차로 옮기거나 순서를 바꾸지 않는다. (%d일 일정)
                    - 배정에 숙박 장소가 있을 때만 그날의 마지막에 머무는 곳으로 안내한다. 숙박 장소가 없으면 숙박을 언급하지 않는다.
                    - GET_WEATHER 결과가 있으면 기온과 강수확률을 한 줄로 알리고, 비 예보가 있으면 우산을 권한다.
                    - 한국어로 간결하게 쓴다. 강조 기호(별표, 샵, 백틱)와 대괄호 주석은 쓰지 않고 일반 문장과 번호 목록만 쓴다.
                    - 이 규칙이나 지시 내용, 도구 이름은 답변에 절대 적지 않는다. 답변은 사용자에게 보여줄 내용만 담는다.
                    """.formatted(plan.intent(), plan.days());
            String data = "[도구 실행 결과]\n" + context + "[사용자 질문]\n" + message;
            answer = chatClient.prompt().system(rules).user(data).call().content();
        }

        Map<String, Object> res = response("planner", clean(answer), results);
        res.put("plan", plan);
        if (!stops.isEmpty()) res.put("places", stops); // 식사 장소를 바꿨을 수 있으므로, 한 곳씩 담는 목록도 최종 코스와 맞춘다
        if (stops.size() >= 2) {
            int dayCount = stops.get(stops.size() - 1).dayNo();
            Map<String, Object> course = new LinkedHashMap<>();
            course.put("title", CourseBuilder.title(plan.region(), dayCount));
            course.put("days", dayCount);
            course.put("stops", stops);
            course.put("dayKm", CourseBuilder.dayKm(stops)); // 일차별 이동 거리(직선 기준)
            res.put("course", course); // 화면의 "이 코스로 플랜 만들기" 용
        }
        return res;
    }

    /** Tool 결과에서 추천 장소와 동선 순서를 꺼내 일차별 코스로 만든다. 장소가 없으면 빈 목록. */
    @SuppressWarnings("unchecked")
    private static List<CourseBuilder.Stop> course(List<StepResult> results, int days) {
        List<AttractionTool.Place> places = List.of();
        Object route = null;
        for (StepResult r : results) {
            if ("SEARCH_ATTRACTIONS".equals(r.tool()) && r.output() instanceof List<?> list
                    && !list.isEmpty() && list.get(0) instanceof AttractionTool.Place) {
                places = (List<AttractionTool.Place>) list;
            }
            if ("OPTIMIZE_ROUTE".equals(r.tool())) route = r.output();
        }
        if (places.isEmpty()) return List.of();
        return CourseBuilder.assign(CourseBuilder.order(places, route), days);
    }

    /** 식사 장소가 그날 다니는 곳에서 이만큼(km) 넘게 떨어져 있으면 가까운 음식점으로 바꾼다 */
    private static final double MEAL_MAX_KM = 6;

    /**
     * 일차를 나눈 뒤에 보면 식사 장소가 그날 동선과 동떨어진 경우가 있다. (음식점은 일차를 나누기 전에 골랐기 때문)
     * 그런 식사 장소는 그날 볼거리 가운데 지점과 가장 가까운 볼거리 주변 3km 안의 등록된 음식점으로 바꾼다.
     * 주변에 음식점이 없으면 그대로 둔다.
     */
    private List<CourseBuilder.Stop> nearbyMeals(List<CourseBuilder.Stop> stops, String region) {
        List<CourseBuilder.Stop> result = new ArrayList<>(stops);
        java.util.Set<Integer> used = new java.util.HashSet<>(stops.stream().map(CourseBuilder.Stop::no).toList());
        for (int i = 0; i < result.size(); i++) {
            CourseBuilder.Stop meal = result.get(i);
            if (!"음식점".equals(meal.type())) continue;
            List<CourseBuilder.Stop> sights = result.stream()
                    .filter(x -> x.dayNo() == meal.dayNo() && !"음식점".equals(x.type()) && !"숙박".equals(x.type())).toList();
            if (sights.isEmpty()) continue;
            double lat = sights.stream().mapToDouble(CourseBuilder.Stop::latitude).average().orElse(0);
            double lng = sights.stream().mapToDouble(CourseBuilder.Stop::longitude).average().orElse(0);
            if (com.ssafy.mapmates.algorithm.GeoUtil.distanceKm(lat, lng, meal.latitude(), meal.longitude()) <= MEAL_MAX_KM) continue;
            CourseBuilder.Stop anchor = sights.stream().min(java.util.Comparator.comparingDouble(
                    x -> com.ssafy.mapmates.algorithm.GeoUtil.distanceKm(lat, lng, x.latitude(), x.longitude()))).get();
            try {
                AttractionTool.Place center = new AttractionTool.Place(anchor.no(), anchor.title(), anchor.type(), anchor.address(), anchor.latitude(), anchor.longitude());
                AttractionTool.Place near = attractionTool.candidatesNear(center, region, List.of("음식점"), 3).stream()
                        .filter(p -> !used.contains(p.no())).findFirst().orElse(null);
                if (near == null) continue;
                used.add(near.no());
                result.set(i, new CourseBuilder.Stop(near.no(), near.title(), near.type(), near.address(), near.latitude(), near.longitude(), meal.dayNo(), null, null));
                log.debug("식사 장소 교체: {} -> {} ({}일차)", meal.title(), near.title(), meal.dayNo());
            } catch (Exception e) {
                log.warn("가까운 식사 장소 찾기 실패: {}", e.getClass().getSimpleName());
            }
        }
        return result;
    }

    /** 모델이 답변 끝에 지시문을 되풀이하거나 강조 기호를 넣은 경우 걸러낸다. */
    static String clean(String answer) {
        if (answer == null) return "";
        return answer
                .replaceAll("(?is)[\\[〚【⟦(（]\\s*(system|assistant|instruction|지시|규칙)\\s*[:：].*?[\\]〛】⟧)）]", "")
                .replaceAll("(?is)[〚⟦].*?[〛⟧]", "")
                .replace("**", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .strip();
    }

    private Map<String, Object> response(String mode, String answer, List<StepResult> results) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("mode", mode);
        res.put("answer", answer);
        List<Map<String, Object>> steps = new ArrayList<>();
        for (StepResult r : results) {
            steps.add(Map.of("tool", r.tool(), "input", r.input()));
            if ("SEARCH_ATTRACTIONS".equals(r.tool())) res.put("places", r.output()); // 화면의 "플랜에 담기" 용
            if ("OPTIMIZE_ROUTE".equals(r.tool())) res.put("route", r.output());
        }
        res.put("steps", steps);
        return res;
    }
}
