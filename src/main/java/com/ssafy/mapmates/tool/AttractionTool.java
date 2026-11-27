package com.ssafy.mapmates.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.dto.Attraction;
import com.ssafy.mapmates.service.AttractionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 관광지 검색 Tool. 실제 조회는 AttractionService(→ AttractionDao)에 위임한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttractionTool {
    private static final Map<String, Integer> TYPES = Map.of("관광지", 12, "문화시설", 14, "축제", 15, "여행코스", 25,
            "레포츠", 28, "숙박", 32, "쇼핑", 38, "음식점", 39);
    private final AttractionService attractionService;

    public record Place(int no, String title, String type, String address, double latitude, double longitude) {}

    @Tool(description = "지역(시도 이름), 키워드, 유형으로 국내 관광지를 검색한다. 결과에는 관광지 번호(no), 이름, 유형, 주소, 위도, 경도가 포함된다.")
    public List<Place> searchAttractions(
            @ToolParam(description = "시도 이름. 예: 서울, 부산, 제주. 없으면 빈 문자열", required = false) String region,
            @ToolParam(description = "장소명에 포함될 키워드. 없으면 빈 문자열", required = false) String keyword,
            @ToolParam(description = "유형: 관광지, 문화시설, 축제, 여행코스, 레포츠, 숙박, 쇼핑, 음식점 중 하나. 없으면 빈 문자열", required = false) String contentType,
            @ToolParam(description = "가져올 개수(1~10)", required = false) Integer count) {
        log.debug("[Tool] searchAttractions region={}, keyword={}, type={}, count={}", region, keyword, contentType, count);
        Integer sidoCode = attractionService.findSidoCode(region);
        Integer typeId = contentType == null ? null : TYPES.get(contentType.trim());
        int size = count == null ? 5 : Math.max(1, Math.min(count, 10));
        @SuppressWarnings("unchecked")
        List<Attraction> items = (List<Attraction>) attractionService.search(sidoCode, null, typeId, keyword, 1, size).get("items");
        return items.stream().filter(a -> a.getLatitude() != null && a.getLongitude() != null)
                .map(a -> new Place(a.getNo(), a.getTitle(), a.getContentTypeName(), a.getAddr1(), a.getLatitude(), a.getLongitude()))
                .toList();
    }
}
