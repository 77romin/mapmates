package com.ssafy.mapmates.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.dao.AttractionDao;
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
    private final AttractionDao attractionDao;

    public record Place(int no, String title, String type, String address, double latitude, double longitude) {}

    /** 장소의 사진과 소개 글 앞부분. 소개 글은 태그와 줄바꿈을 걷어낸 한 줄이다. */
    public record Brief(String image, String overview) {}

    /** 번호별 사진·소개 글. AI 가 이름만 보고 고르지 않도록 등록된 소개 글을 근거로 함께 넘길 때 쓴다. */
    public Map<Integer, Brief> briefs(List<Integer> nos) {
        return briefs(nos, false);
    }

    /**
     * @param fill true 면 소개 글이 비어 있는 장소를 한국관광공사 API 에서 한꺼번에 찾아 채운다.
     *             (최종 코스에 들어간 몇 곳에만 쓴다. 전체 대기는 7초로 제한한다)
     */
    public Map<Integer, Brief> briefs(List<Integer> nos, boolean fill) {
        Map<Integer, Brief> result = new java.util.HashMap<>();
        if (nos == null || nos.isEmpty()) return result;
        List<Attraction> rows = attractionDao.findBriefs(nos);
        if (fill) {
            try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>();
                for (Attraction a : rows) {
                    if (a.getOverview() == null || a.getOverview().isBlank()) jobs.add(pool.submit(() -> attractionService.fillOverview(a)));
                }
                long deadline = System.currentTimeMillis() + 7000;
                for (var job : jobs) {
                    try {
                        job.get(Math.max(1, deadline - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        job.cancel(true); // 늦는 곳은 소개 없이 진행한다
                    }
                }
            }
        }
        for (Attraction a : rows) {
            String text = a.getOverview() == null ? "" : a.getOverview().replaceAll("<[^>]*>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", " ").strip();
            String image = a.getFirstImage1() == null || a.getFirstImage1().isBlank() ? null : a.getFirstImage1();
            result.put(a.getNo(), new Brief(image, text));
        }
        return result;
    }

    /** 글을 지정한 길이에서 자른다. (프롬프트 길이를 줄이기 위함) */
    public static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

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

    @Tool(description = "장소 이름으로 실제 등록된 장소를 확인한다. 등록되어 있으면 번호(no), 정식 이름, 유형, 주소, 좌표를 반환하고 없으면 null 을 반환한다.")
    public Place findPlaceByName(
            @ToolParam(description = "시도 이름. 예: 서울, 부산. 없으면 빈 문자열", required = false) String region,
            @ToolParam(description = "확인할 장소 이름. 예: 경복궁, 남산서울타워") String name) {
        Attraction a = attractionService.findByName(attractionService.findSidoCode(region), name);
        log.debug("[Tool] findPlaceByName region={}, name={} -> {}", region, name, a == null ? "없음" : a.getTitle());
        return a == null ? null : toPlace(a);
    }

    /** 이름이 비슷한 등록 장소 후보들 (Planner 가 AI 추천 이름을 실제 장소와 맞출 때 사용) */
    public List<Place> findCandidatesByName(String region, String name) {
        return attractionService.findCandidatesByName(attractionService.findSidoCode(region), name, 6).stream().map(this::toPlace).toList();
    }

    /** 기준 장소 주변에서 부족한 수만큼 채울 후보 (Planner 가 사용) */
    public List<Place> candidatesNear(Place center, String region, List<String> contentTypes, double radiusKm) {
        Attraction anchor = attractionService.get(center.no());
        return attractionService.candidatesNear(anchor, attractionService.findSidoCode(region), typeIds(contentTypes), radiusKm, 18).stream()
                .map(this::toPlace).toList();
    }

    private Place toPlace(Attraction a) {
        return new Place(a.getNo(), a.getTitle(), a.getContentTypeName(), a.getAddr1(), a.getLatitude(), a.getLongitude());
    }

    private List<Integer> typeIds(List<String> contentTypes) {
        List<Integer> ids = contentTypes == null ? List.of()
                : contentTypes.stream().map(t -> TYPES.get(t == null ? "" : t.trim())).filter(t -> t != null).distinct().toList();
        return ids.isEmpty() ? List.of(12, 14, 39) : ids;
    }

    @Tool(description = "여행 코스에 넣을 후보 장소를 조회한다. 지역 안에서 서로 가까운 장소들을 유형별로 섞어 매번 다르게 반환한다. 반환된 후보 중에서 사용자의 조건에 맞는 장소를 골라 써야 한다.")
    public List<Place> findCourseCandidates(
            @ToolParam(description = "시도 이름. 예: 서울, 부산. 없으면 빈 문자열", required = false) String region,
            @ToolParam(description = "포함할 유형 목록: 관광지, 문화시설, 축제, 레포츠, 숙박, 쇼핑, 음식점", required = false) List<String> contentTypes,
            @ToolParam(description = "장소명·주소에 포함될 키워드. 없으면 빈 문자열", required = false) String keyword,
            @ToolParam(description = "후보를 모을 반경(km). 당일 5, 1박 이상 12 권장", required = false) Double radiusKm) {
        log.debug("[Tool] findCourseCandidates region={}, types={}, keyword={}, radius={}", region, contentTypes, keyword, radiusKm);
        List<Integer> typeIds = typeIds(contentTypes);
        double radius = radiusKm == null ? 6 : Math.max(2, Math.min(radiusKm, 30));
        return attractionService.candidates(attractionService.findSidoCode(region), typeIds, keyword, radius, 18).stream()
                .map(a -> new Place(a.getNo(), a.getTitle(), a.getContentTypeName(), a.getAddr1(), a.getLatitude(), a.getLongitude()))
                .toList();
    }
}
