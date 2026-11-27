package com.ssafy.mapmates.planner;

import java.util.List;

/** Planner 가 만든 실행 계획: 어떤 Tool 을 어떤 순서로, 어떤 입력으로 호출할지 */
/**
 * @param contentTypes 코스에 섞을 장소 유형 (예: 데이트 → 관광지, 문화시설, 음식점)
 * @param days         여행 일수 (당일 = 1)
 * @param intent       사용자가 원한 조건 요약 (예: "연인과 당일 데이트")
 * @param pace         일정의 밀도: 여유 / 보통 / 알차게
 */
public record ExecutionPlan(List<ToolStep> steps, String region, String keyword, String contentType, Integer count,
        String documentQuery, List<String> contentTypes, Integer days, String intent, String pace) {

    /** 하루에 넣을 볼거리 수 (밀도에 따라) */
    public int sightsPerDay() {
        return "여유".equals(pace) ? 3 : "알차게".equals(pace) ? 6 : 5;
    }

    /** 하루에 넣을 식사 수: 점심과 저녁 */
    public static final int MEALS_PER_DAY = 2;

    public enum ToolStep {
        SEARCH_ATTRACTIONS, GET_WEATHER, OPTIMIZE_ROUTE, GET_MY_PLANS, SEARCH_PERFORMANCES, SEARCH_DOCUMENTS
    }
}
