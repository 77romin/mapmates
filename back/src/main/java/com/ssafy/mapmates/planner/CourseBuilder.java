package com.ssafy.mapmates.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.mapmates.tool.AttractionTool.Place;

/**
 * 추천된 장소를 일차별 코스로 묶는다.
 * 화면은 이 결과로 플랜을 바로 만들고, 답변 문장도 같은 배정을 따르게 해서 글과 플랜이 어긋나지 않게 한다.
 */
public final class CourseBuilder {
    private CourseBuilder() {}

    /** 코스의 방문지 하나. dayNo 는 1부터 시작한다. */
    public record Stop(int no, String title, String type, String address, double latitude, double longitude, int dayNo) {}

    /** 동선 최적화 결과(order)가 있으면 그 순서로, 없으면 추천 순서 그대로 둔다. */
    public static List<Place> order(List<Place> places, Object route) {
        if (!(route instanceof Map<?, ?> map) || !(map.get("order") instanceof List<?> order)) return places;
        Map<Integer, Place> byNo = new LinkedHashMap<>();
        places.forEach(p -> byNo.put(p.no(), p));
        List<Place> ordered = new ArrayList<>();
        for (Object o : order) {
            if (o instanceof Number n && byNo.containsKey(n.intValue())) ordered.add(byNo.remove(n.intValue()));
        }
        ordered.addAll(byNo.values()); // 최적화 결과에 빠진 장소가 있으면 뒤에 붙인다
        return ordered;
    }

    /**
     * 방문 순서를 유지한 채 일수만큼 고르게 나눈다.
     * 숙박은 하루의 마지막 방문지여야 하므로 따로 빼 두었다가 1일차부터 차례로 그날 끝에 붙인다.
     */
    public static List<Stop> assign(List<Place> ordered, int days) {
        List<Place> stays = ordered.stream().filter(p -> "숙박".equals(p.type())).toList();
        List<Place> visits = ordered.stream().filter(p -> !"숙박".equals(p.type())).toList();
        int dayCount = Math.max(1, Math.min(days, Math.max(1, visits.size())));
        List<List<Place>> byDay = new ArrayList<>();
        for (int d = 0; d < dayCount; d++) byDay.add(new ArrayList<>());
        for (int i = 0; i < visits.size(); i++) byDay.get(i * dayCount / visits.size()).add(visits.get(i));
        // 숙박은 마지막 날을 뺀 날의 끝에 하나씩. 남으면(또는 당일 일정이면) 마지막으로 잔 날 끝에 붙인다
        int nights = Math.max(1, dayCount - 1);
        for (int i = 0; i < stays.size(); i++) byDay.get(Math.min(i, nights - 1)).add(stays.get(i));

        List<Stop> stops = new ArrayList<>();
        for (int d = 0; d < dayCount; d++) {
            for (Place p : byDay.get(d)) stops.add(new Stop(p.no(), p.title(), p.type(), p.address(), p.latitude(), p.longitude(), d + 1));
        }
        return stops;
    }

    /** 예: "부산 1박 2일 코스", "서울 당일 코스" */
    public static String title(String region, int dayCount) {
        String where = region == null || region.isBlank() ? "" : region + " ";
        return where + (dayCount <= 1 ? "당일" : (dayCount - 1) + "박 " + dayCount + "일") + " 코스";
    }

    /** 답변을 쓰는 모델에게 넘길 일차 배정 설명 */
    public static String describe(List<Stop> stops) {
        StringBuilder sb = new StringBuilder();
        int day = 0;
        int seq = 0;
        for (Stop s : stops) {
            if (s.dayNo() != day) {
                day = s.dayNo();
                seq = 0;
                sb.append(day).append("일차:\n");
            }
            sb.append("  ").append(++seq).append(". ").append(s.title()).append(" (").append(s.type()).append(")\n");
        }
        return sb.toString();
    }
}
