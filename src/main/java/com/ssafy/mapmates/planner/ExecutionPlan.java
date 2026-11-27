package com.ssafy.mapmates.planner;

import java.util.List;

/** Planner 가 만든 실행 계획: 어떤 Tool 을 어떤 순서로, 어떤 입력으로 호출할지 */
/**
 * @param contentTypes 코스에 섞을 장소 유형 (예: 데이트 → 관광지, 문화시설, 음식점)
 * @param days         여행 일수 (당일 = 1)
 * @param intent       사용자가 원한 조건 요약 (예: "연인과 당일 데이트")
 */
public record ExecutionPlan(List<ToolStep> steps, String region, String keyword, String contentType, Integer count,
        String documentQuery, List<String> contentTypes, Integer days, String intent) {

    public enum ToolStep {
        SEARCH_ATTRACTIONS, GET_WEATHER, OPTIMIZE_ROUTE, GET_MY_PLANS, SEARCH_PERFORMANCES, SEARCH_DOCUMENTS
    }
}
