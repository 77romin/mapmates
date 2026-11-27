package com.ssafy.mapmates.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.ssafy.mapmates.common.ApiException;
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

    public Map<String, Object> planner(String message, Integer memberId) {
        ExecutionPlan plan = tripPlanner.plan(message, memberId != null);
        log.debug("실행 계획: {}", plan);
        List<StepResult> results = tripPlanner.execute(plan, memberId);

        // Tool 실행 결과(Resource)를 Prompt 에 통합한다. LLM 은 이 데이터만 근거로 답한다.
        StringBuilder context = new StringBuilder();
        for (StepResult r : results) {
            context.append("### ").append(r.tool()).append(" (입력: ").append(r.input()).append(")\n").append(r.output()).append("\n\n");
        }
        String prompt = results.isEmpty() ? message
                : """
                아래는 서버가 도구를 실행해 얻은 결과다. 이 결과에 있는 사실만 사용해 사용자 질문에 답하라.
                결과에 없는 장소나 수치는 만들지 말고, 비어 있는 결과는 "조회된 정보가 없다"고 알려라.
                OPTIMIZE_ROUTE 결과가 있으면 order 의 번호 순서대로 장소를 안내하고 이동 거리를 함께 알려라.
                사용자 조건(%s)에 맞춰 설명하고, %d일 일정이면 일차별로 나눠 안내하라. 장소마다 왜 이 조건에 어울리는지 한마디를 붙여라.
                강조 기호(별표 두 개, 샵, 백틱)는 쓰지 말고 일반 텍스트와 번호 목록만 사용하라.

                [도구 실행 결과]
                %s
                [사용자 질문]
                %s
                """.formatted(plan.intent(), plan.days(), context, message);
        String answer = chatClient.prompt().user(prompt).call().content();

        Map<String, Object> res = response("planner", answer, results);
        res.put("plan", plan);
        return res;
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
