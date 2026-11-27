package com.ssafy.mapmates.algorithm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 알고리즘 #2. 취향 태그 기반 추천 (코사인 유사도)
 * 태그 집합을 0/1 벡터로 보면 cos = |A∩B| / (√|A| · √|B|). 시간 O(|A| + |B|)
 *
 * 알고리즘 #3. 메이트 중간 집결지 (기하 중앙값, Weiszfeld 반복법)
 * 모든 출발지로부터의 거리 합이 최소가 되는 지점을 반복적으로 근사한다. 시간 O(반복수 · n)
 */
public final class Recommender {
    private Recommender() {}

    public static Set<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return Set.of();
        return Arrays.stream(tags.split("[,#\\s]+")).map(String::trim).filter(t -> !t.isEmpty())
                .map(String::toLowerCase).collect(Collectors.toCollection(HashSet::new));
    }

    /** 두 태그 집합의 코사인 유사도 (0 ~ 1) */
    public static double cosine(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        long inter = a.stream().filter(b::contains).count();
        return inter / (Math.sqrt(a.size()) * Math.sqrt(b.size()));
    }

    /** points = {lat, lng} 목록의 기하 중앙값 */
    public static double[] geometricMedian(List<double[]> points) {
        if (points.isEmpty()) return null;
        double lat = points.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double lng = points.stream().mapToDouble(p -> p[1]).average().orElse(0);
        for (int iter = 0; iter < 100; iter++) {
            double numLat = 0, numLng = 0, den = 0;
            for (double[] p : points) {
                double dist = Math.max(GeoUtil.distanceKm(lat, lng, p[0], p[1]), 1e-6);
                numLat += p[0] / dist;
                numLng += p[1] / dist;
                den += 1 / dist;
            }
            double nLat = numLat / den, nLng = numLng / den;
            boolean converged = Math.abs(nLat - lat) < 1e-7 && Math.abs(nLng - lng) < 1e-7;
            lat = nLat;
            lng = nLng;
            if (converged) break;
        }
        return new double[] { lat, lng };
    }
}
