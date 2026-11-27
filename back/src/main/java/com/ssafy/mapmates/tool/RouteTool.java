package com.ssafy.mapmates.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.service.PlanService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 동선 최적화 Tool. 실제 계산은 PlanService(→ RouteOptimizer)에 위임한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RouteTool {
    private final PlanService planService;

    @Tool(description = "관광지 번호(no) 목록을 받아 총 이동 거리가 가장 짧은 방문 순서를 계산한다. 첫 번째 번호가 출발지다. 최적화 전후 거리(km)와 순서를 반환한다.")
    public Map<String, Object> optimizeRoute(@ToolParam(description = "관광지 번호 목록(2개 이상)") List<Integer> attractionNos) {
        log.debug("[Tool] optimizeRoute nos={}", attractionNos);
        Map<String, Object> result = planService.optimize(attractionNos);
        result.remove("attractions"); // LLM 에는 순서와 거리만 전달
        return result;
    }
}
