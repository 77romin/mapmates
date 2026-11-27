package com.ssafy.mapmates.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.service.AttractionService;
import com.ssafy.mapmates.service.CultureService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 공연 검색 Tool (외부 API: KOPIS). 실제 연동은 CultureService 에 위임한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CultureTool {
    private final CultureService cultureService;
    private final AttractionService attractionService;

    @Tool(description = "지역(시도 이름)과 키워드로 오늘부터 30일 이내에 열리는 공연(연극, 뮤지컬, 콘서트 등) 목록을 조회한다. 공연명, 기간, 공연장, 장르가 포함된다.")
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> searchPerformances(
            @ToolParam(description = "시도 이름. 예: 서울, 부산. 없으면 빈 문자열", required = false) String region,
            @ToolParam(description = "공연명에 포함될 키워드. 없으면 빈 문자열", required = false) String keyword) {
        log.debug("[Tool] searchPerformances region={}, keyword={}", region, keyword);
        if (!cultureService.kopisAvailable()) return List.of(Map.of("message", "공연 정보 API 키가 설정되지 않아 조회할 수 없습니다."));
        Integer sidoCode = attractionService.findSidoCode(region);
        List<Map<String, String>> items = (List<Map<String, String>>) cultureService.performances(sidoCode, keyword, 1, 5).get("items");
        return items.stream().map(m -> Map.of("title", nv(m.get("title")), "period", nv(m.get("startDate")) + " ~ " + nv(m.get("endDate")),
                "venue", nv(m.get("venue")), "genre", nv(m.get("genre")), "state", nv(m.get("state")))).toList();
    }

    private String nv(String s) {
        return s == null ? "" : s;
    }
}
