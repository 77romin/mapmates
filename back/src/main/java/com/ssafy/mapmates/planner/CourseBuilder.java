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

    /**
     * 코스의 방문지 하나. dayNo 는 1부터 시작한다.
     * slot 은 하루 중 자리(오전, 점심, 오후, 저녁, 숙박). image·summary 는 등록된 사진과 소개 글 첫머리(없으면 null).
     */
    public record Stop(int no, String title, String type, String address, double latitude, double longitude, int dayNo,
            String slot, String image, String summary) {}

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
     * 하루를 "오전 볼거리 → 점심 → 오후 볼거리 → 저녁 → 숙박" 틀로 짠다.
     * 1) 볼거리는 동선 순서를 유지한 채 일수만큼 고르게 나눈다. (동선상 가까운 곳끼리 같은 날이 된다)
     * 2) 하루의 볼거리를 앞 절반(오전)과 뒤 절반(오후)으로 나눈다.
     * 3) 점심은 오전 마지막 장소와 오후 첫 장소의 중간에서 가장 가까운 음식점, 저녁은 그날 마지막 볼거리와 가장 가까운 음식점.
     *    점심을 모든 날에 먼저 채운 뒤 저녁을 채운다. (음식점이 모자라면 저녁이 비는 날이 생긴다)
     * 4) 숙박은 마지막 날을 뺀 날의 끝에 하나씩 둔다.
     */
    public static List<Stop> assign(List<Place> ordered, int days) {
        List<Place> stays = new ArrayList<>(ordered.stream().filter(p -> "숙박".equals(p.type())).toList());
        List<Place> foods = new ArrayList<>(ordered.stream().filter(p -> "음식점".equals(p.type())).toList());
        List<Place> visits = new ArrayList<>(ordered.stream().filter(p -> !"숙박".equals(p.type()) && !"음식점".equals(p.type())).toList());
        boolean foodOnly = visits.isEmpty(); // 맛집 위주 요청: 음식점이 곧 방문지다. 오전·오후로 나누지 않는다
        if (foodOnly) {
            visits = foods;
            foods = new ArrayList<>();
        }
        int dayCount = Math.max(1, Math.min(days, Math.max(1, visits.size())));
        List<List<Place>> byDay = new ArrayList<>();
        for (int d = 0; d < dayCount; d++) byDay.add(new ArrayList<>());
        for (int i = 0; i < visits.size(); i++) byDay.get(i * dayCount / visits.size()).add(visits.get(i));
        tighten(byDay);

        Place[] lunch = new Place[dayCount];
        Place[] dinner = new Place[dayCount];
        for (int d = 0; d < dayCount && !foods.isEmpty(); d++) { // 점심
            List<Place> day = byDay.get(d);
            if (day.isEmpty()) continue;
            int am = (day.size() + 1) / 2;
            Place before = day.get(am - 1);
            Place after = day.get(Math.min(am, day.size() - 1));
            lunch[d] = takeNearest(foods, (before.latitude() + after.latitude()) / 2, (before.longitude() + after.longitude()) / 2);
        }
        for (int d = 0; d < dayCount && !foods.isEmpty(); d++) { // 저녁
            List<Place> day = byDay.get(d);
            if (day.isEmpty()) continue;
            Place last = day.get(day.size() - 1);
            dinner[d] = takeNearest(foods, last.latitude(), last.longitude());
        }
        int nights = Math.max(1, dayCount - 1);

        List<Stop> stops = new ArrayList<>();
        for (int d = 0; d < dayCount; d++) {
            List<Place> day = byDay.get(d);
            int am = (day.size() + 1) / 2;
            for (int i = 0; i < day.size(); i++) {
                if (i == am && lunch[d] != null) stops.add(stop(lunch[d], d + 1, "점심"));
                // 볼거리가 한 곳뿐인 날은 오전·오후를 나누지 않는다
                stops.add(stop(day.get(i), d + 1, foodOnly ? "" : day.size() == 1 ? "오후" : i < am ? "오전" : "오후"));
            }
            if (lunch[d] != null && am >= day.size()) stops.add(stop(lunch[d], d + 1, "점심")); // 볼거리가 한 곳이면 그 뒤에 점심
            if (dinner[d] != null) stops.add(stop(dinner[d], d + 1, "저녁"));
            for (int i = 0; i < stays.size(); i++) {
                if (Math.min(i, nights - 1) == d) stops.add(stop(stays.get(i), d + 1, "숙박"));
            }
        }
        return stops;
    }

    private static Stop stop(Place p, int dayNo, String slot) {
        return new Stop(p.no(), p.title(), p.type(), p.address(), p.latitude(), p.longitude(), dayNo, slot, null, null);
    }

    /** 하루 볼거리를 잇는 거리가 이만큼(km, 직선 기준)을 넘으면 동떨어진 곳을 옮기거나 뺀다 */
    static final double DAY_MAX_KM = 25;
    /** 다른 날의 가운데 지점과 이만큼(km) 안이면 그날로 옮긴다 */
    static final double MOVE_WITHIN_KM = 8;

    /**
     * 하루가 너무 흩어지지 않게 한다.
     * 하루 이동 거리가 상한을 넘으면 그날 나머지 장소들과 가장 동떨어진 곳을 고른다.
     * 그곳이 다른 날 장소들과 가까우면 그날로 옮기고(가장 가까운 장소 바로 뒤), 아니면 코스에서 뺀다.
     * 하루에 두 곳만 남으면 더 줄이지 않는다.
     */
    static void tighten(List<List<Place>> byDay) {
        for (int round = 0; round < 12; round++) {
            boolean changed = false;
            for (int d = 0; d < byDay.size(); d++) {
                List<Place> day = byDay.get(d);
                if (day.size() <= 2 || pathKm(day) <= DAY_MAX_KM) continue;
                Place far = day.get(0);
                double farKm = -1;
                for (Place p : day) { // 나머지 장소들의 가운데에서 가장 먼 곳
                    double lat = day.stream().filter(x -> x != p).mapToDouble(Place::latitude).average().orElse(p.latitude());
                    double lng = day.stream().filter(x -> x != p).mapToDouble(Place::longitude).average().orElse(p.longitude());
                    double km = GeoUtil.distanceKm(lat, lng, p.latitude(), p.longitude());
                    if (km > farKm) {
                        farKm = km;
                        far = p;
                    }
                }
                day.remove(far);
                int to = -1;
                double best = MOVE_WITHIN_KM;
                for (int e = 0; e < byDay.size(); e++) {
                    List<Place> other = byDay.get(e);
                    if (e == d || other.isEmpty()) continue;
                    double lat = other.stream().mapToDouble(Place::latitude).average().orElse(0);
                    double lng = other.stream().mapToDouble(Place::longitude).average().orElse(0);
                    double km = GeoUtil.distanceKm(lat, lng, far.latitude(), far.longitude());
                    if (km < best) {
                        best = km;
                        to = e;
                    }
                }
                if (to >= 0) {
                    List<Place> other = byDay.get(to);
                    int at = 0;
                    for (int i = 1; i < other.size(); i++) {
                        if (GeoUtil.distanceKm(other.get(i).latitude(), other.get(i).longitude(), far.latitude(), far.longitude())
                                < GeoUtil.distanceKm(other.get(at).latitude(), other.get(at).longitude(), far.latitude(), far.longitude())) at = i;
                    }
                    other.add(at + 1, far);
                }
                changed = true;
            }
            if (!changed) break;
        }
    }

    private static double pathKm(List<Place> day) {
        double km = 0;
        for (int i = 1; i < day.size(); i++) {
            km += GeoUtil.distanceKm(day.get(i - 1).latitude(), day.get(i - 1).longitude(), day.get(i).latitude(), day.get(i).longitude());
        }
        return km;
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
            return new Stop(s.no(), s.title(), s.type(), s.address(), s.latitude(), s.longitude(), s.dayNo(), s.slot(), b.image(), summary);
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

    /** 답변을 쓰는 모델에게 넘길 일차 배정 설명 (하루 중 자리와 일차별 이동 거리 포함) */
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
            sb.append("  ").append(++seq).append(". ").append(s.slot().isBlank() ? "" : "[" + s.slot() + "] ").append(s.title()).append(" (").append(s.type()).append(")\n");
        }
        return sb.toString();
    }
}
