package com.ssafy.mapmates.planner;

import java.util.List;

/** Planner 가 만든 실행 계획: 어떤 Tool 을 어떤 순서로, 어떤 입력으로 호출할지 */
public record ExecutionPlan(List<ToolStep> steps, String region, String keyword, String contentType, Integer count,
        String documentQuery) {

    public enum ToolStep {
        SEARCH_ATTRACTIONS, GET_WEATHER, OPTIMIZE_ROUTE, GET_MY_PLANS, SEARCH_DOCUMENTS
    }
}
