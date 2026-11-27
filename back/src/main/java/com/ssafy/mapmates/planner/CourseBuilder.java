package com.ssafy.mapmates.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.mapmates.algorithm.GeoUtil;
import com.ssafy.mapmates.tool.AttractionTool.Brief;
import com.ssafy.mapmates.tool.AttractionTool.Place;

/**
 * 추천된 장소를 일차별 코스로 묶는다.
 * 화면은 이 결과로 플랜을 바로 만들고, 답변 문장도 같은 배정을 따르게 해서 글과 플랜이 어긋나지 않게 한다.
 */
public final class CourseBuilder {
    private CourseBuilder() {}

    /** 코스의 방문지 하나. dayNo 는 1부터 시작한다. image·summary 는 등록된 사진과 소개 글 첫머리(없으면 null). */
    public record Stop(int no, String title, String type, String address, double latitude, double longitude, int dayNo,
            String image, String summary) {}

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
     * 하루 일정처럼 보이도록 일차를 나눈다.
     * 1) 볼거리는 동선 순서를 유지한 채 일수만큼 고르게 나눈다. (동선상 가까운 곳끼리 같은 날이 된다)
     * 2) 음식점은 하루에 한 곳씩, 그날 볼거리와 가장 가까운 곳을 일정 가운데(점심)에 넣는다.
     * 3) 음식점이 남으면 하루에 한 곳씩, 그날 마지막 장소와 가장 가까운 곳을 끝(저녁)에 넣는다.
     * 4) 숙박은 마지막 날을 뺀 날의 끝에 하나씩 둔다.
     */
    public static List<Stop> assign(List<Place> ordered, int days) {
        List<Place> stays = new ArrayList<>(ordered.stream().filter(p -> "숙박".equals(p.type())).toList());
        List<Place> foods = new ArrayList<>(ordered.stream().filter(p -> "음식점".equals(p.type())).toList());
        List<Place> visits = new ArrayList<>(ordered.stream().filter(p -> !"숙박".equals(p.type()) && !"음식점".equals(p.type())).toList());
        if (visits.isEmpty()) { // 맛집 위주 요청: 음식점이 곧 방문지다
            visits = foods;
            foods = new ArrayList<>();
        }
        int dayCount = Math.max(1, Math.min(days, Math.max(1, visits.size())));
        List<List<Place>> byDay = new ArrayList<>();
        for (int d = 0; d < dayCount; d++) byDay.add(new ArrayList<>());
        for (int i = 0; i < visits.size(); i++) byDay.get(i * dayCount / visits.size()).add(visits.get(i));

        for (List<Place> day : byDay) { // 점심: 그날 장소들의 가운데 지점과 가장 가까운 음식점
            if (foods.isEmpty() || day.isEmpty()) continue;
            double lat = day.stream().mapToDouble(Place::latitude).average().orElse(0);
            double lng = day.stream().mapToDouble(Place::longitude).average().orElse(0);
            day.add((day.size() + 1) / 2, takeNearest(foods, lat, lng));
        }
        for (List<Place> day : byDay) { // 저녁: 그날 마지막 장소와 가장 가까운 음식점
            if (foods.isEmpty() || day.isEmpty()) continue;
            Place last = day.get(day.size() - 1);
            day.add(takeNearest(foods, last.latitude(), last.longitude()));
        }
        for (int i = 0; !foods.isEmpty(); i++) byDay.get(i % dayCount).add(foods.remove(0)); // 그래도 남으면 차례로 뒤에

        int nights = Math.max(1, dayCount - 1);
        for (int i = 0; i < stays.size(); i++) byDay.get(Math.min(i, nights - 1)).add(stays.get(i));

        List<Stop> stops = new ArrayList<>();
        for (int d = 0; d < dayCount; d++) {
            for (Place p : byDay.get(d)) stops.add(new Stop(p.no(), p.title(), p.type(), p.address(), p.latitude(), p.longitude(), d + 1, null, null));
        }
        return stops;
    }

    private static Place takeNearest(List<Place> from, double lat, double lng) {
        Place best = from.get(0);
        for (Place p : from) {
            if (GeoUtil.distanceKm(lat, lng, p.latitude(), p.longitude()) < GeoUtil.distanceKm(lat, lng, best.latitude(), best.longitude())) best = p;
        }
        from.remove(best);
        return best;
    }

    /** 등록된 사진과 소개 글 첫머리를 붙인다. */
    public static List<Stop> withBriefs(List<Stop> stops, Map<Integer, Brief> briefs) {
        return stops.stream().map(s -> {
            Brief b = briefs.get(s.no());
            if (b == null) return s;
            String summary = b.overview().isBlank() ? null : firstSentence(b.overview());
            return new Stop(s.no(), s.title(), s.type(), s.address(), s.latitude(), s.longitude(), s.dayNo(), b.image(), summary);
        }).toList();
    }

    /** 소개 글의 첫 문장 (너무 길면 70자에서 자른다) */
    static String firstSentence(String text) {
        int end = text.indexOf("다.");
        String first = end > 10 && end < 90 ? text.substring(0, end + 2) : text;
        return first.length() <= 70 ? first : first.substring(0, 70) + "…";
    }

    /** 일차별 이동 거리(km, 직선 기준). 인덱스 0 이 1일차다. */
    public static List<Double> dayKm(List<Stop> stops) {
        List<Double> km = new ArrayList<>();
        Stop prev = null;
        for (Stop s : stops) {
            while (km.size() < s.dayNo()) km.add(0.0);
            if (prev != null && prev.dayNo() == s.dayNo()) {
                km.set(s.dayNo() - 1, km.get(s.dayNo() - 1) + GeoUtil.distanceKm(prev.latitude(), prev.longitude(), s.latitude(), s.longitude()));
            }
            prev = s;
        }
        return km.stream().map(v -> Math.round(v * 10) / 10.0).toList();
    }

    /** 예: "부산 1박 2일 코스", "서울 당일 코스" */
    public static String title(String region, int dayCount) {
        String where = region == null || region.isBlank() ? "" : region + " ";
        return where + (dayCount <= 1 ? "당일" : (dayCount - 1) + "박 " + dayCount + "일") + " 코스";
    }

    /** 답변을 쓰는 모델에게 넘길 일차 배정 설명 (일차별 이동 거리 포함) */
    public static String describe(List<Stop> stops) {
        List<Double> km = dayKm(stops);
        StringBuilder sb = new StringBuilder();
        int day = 0;
        int seq = 0;
        for (Stop s : stops) {
            if (s.dayNo() != day) {
                day = s.dayNo();
                seq = 0;
                sb.append(day).append("일차 (이동 약 ").append(km.get(day - 1)).append("km):\n");
            }
            sb.append("  ").append(++seq).append(". ").append(s.title()).append(" (").append(s.type()).append(")\n");
        }
        return sb.toString();
    }
}
