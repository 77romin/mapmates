package com.ssafy.mapmates.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.ssafy.mapmates.algorithm.GeoUtil;
import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.dto.Attraction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이동 수단별 실제 경로 조회
 * - car / walk : OSRM(OpenStreetMap 기반 경로 탐색 서버)으로 도로를 따라가는 경로·거리·시간을 구한다.
 * - transit    : 공개된 무료 대중교통 경로 API 가 없어, 구간별 직선 거리와 카카오맵 대중교통 길찾기 링크를 제공한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {
    private final AttractionService attractionService;
    private final RestClient restClient = RestClient.builder().requestFactory(timeoutFactory()).build();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    @Value("${mapmates.routing.url:https://routing.openstreetmap.de}")
    private String routingUrl;

    public Map<String, Object> route(String mode, List<Integer> attractionNos) {
        List<Attraction> stops = attractionService.findByNos(attractionNos);
        if (stops.size() < 2) throw ApiException.badRequest("경로를 보려면 방문지가 2곳 이상 필요합니다.");
        if (stops.size() > 25) throw ApiException.badRequest("경로는 방문지 25곳까지 볼 수 있습니다.");
        return switch (mode == null ? "" : mode) {
            case "car" -> osrm("car", "routed-car", "driving", stops);
            case "walk" -> osrm("walk", "routed-foot", "foot", stops);
            case "transit" -> transit(stops);
            default -> throw ApiException.badRequest("mode 는 car, walk, transit 중 하나여야 합니다.");
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> osrm(String mode, String server, String profile, List<Attraction> stops) {
        String coords = stops.stream().map(a -> a.getLongitude() + "," + a.getLatitude()).collect(Collectors.joining(";"));
        String url = routingUrl + "/" + server + "/route/v1/" + profile + "/" + coords + "?overview=full&geometries=geojson";
        Map<String, Object> cached = cache.get(url);
        if (cached != null) return cached;
        try {
            Map<String, Object> body = restClient.get().uri(URI.create(url)).retrieve().body(Map.class);
            List<Map<String, Object>> routes = body == null ? null : (List<Map<String, Object>>) body.get("routes");
            if (routes == null || routes.isEmpty()) throw new IllegalStateException("no route");
            Map<String, Object> route = routes.get(0);

            // GeoJSON 좌표는 [경도, 위도] 순서 -> 화면에서 쓰기 쉽게 [위도, 경도] 로 바꾼다
            List<List<Number>> line = (List<List<Number>>) ((Map<String, Object>) route.get("geometry")).get("coordinates");
            List<double[]> path = new ArrayList<>(line.size());
            for (List<Number> p : line) path.add(new double[] { p.get(1).doubleValue(), p.get(0).doubleValue() });

            List<Map<String, Object>> legs = new ArrayList<>();
            List<Map<String, Object>> rawLegs = (List<Map<String, Object>>) route.get("legs");
            for (int i = 0; i < rawLegs.size(); i++) {
                legs.add(leg(stops.get(i), stops.get(i + 1), km(rawLegs.get(i).get("distance")), minutes(rawLegs.get(i).get("duration")), null));
            }
            Map<String, Object> result = result(mode, true, km(route.get("distance")), minutes(route.get("duration")), path, legs);
            if (cache.size() > 500) cache.clear();
            cache.put(url, result);
            return result;
        } catch (Exception e) {
            log.warn("경로 조회 실패({}): {}", mode, e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "경로 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private Map<String, Object> transit(List<Attraction> stops) {
        List<double[]> path = new ArrayList<>();
        List<Map<String, Object>> legs = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < stops.size(); i++) {
            Attraction a = stops.get(i);
            path.add(new double[] { a.getLatitude(), a.getLongitude() });
            if (i + 1 < stops.size()) {
                Attraction b = stops.get(i + 1);
                double d = Math.round(GeoUtil.distanceKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude()) * 10) / 10.0;
                total += d;
                String link = "https://map.kakao.com/link/by/traffic/" + place(a) + "/" + place(b);
                legs.add(leg(a, b, d, null, link));
            }
        }
        Map<String, Object> result = result("transit", false, Math.round(total * 10) / 10.0, null, path, legs);
        result.put("message", "대중교통은 구간별 직선 거리만 표시합니다. 실제 노선과 소요 시간은 각 구간의 카카오맵 길찾기에서 확인하세요.");
        return result;
    }

    private Map<String, Object> result(String mode, boolean exact, double distanceKm, Integer durationMin, List<double[]> path, List<Map<String, Object>> legs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", mode);
        m.put("exact", exact); // true: 실제 도로 경로, false: 직선
        m.put("distanceKm", distanceKm);
        m.put("durationMin", durationMin);
        m.put("legs", legs);
        m.put("path", path);
        return m;
    }

    private Map<String, Object> leg(Attraction from, Attraction to, double distanceKm, Integer durationMin, String link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from.getTitle());
        m.put("to", to.getTitle());
        m.put("distanceKm", distanceKm);
        m.put("durationMin", durationMin);
        m.put("link", link);
        return m;
    }

    /** 카카오맵 길찾기 링크의 "이름,위도,경도" 구간 */
    private String place(Attraction a) {
        String name = a.getTitle().replace(",", " ").replace("/", " ");
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20") + "," + a.getLatitude() + "," + a.getLongitude();
    }

    private double km(Object meters) {
        return Math.round(((Number) meters).doubleValue() / 100.0) / 10.0;
    }

    private int minutes(Object seconds) {
        return (int) Math.max(1, Math.round(((Number) seconds).doubleValue() / 60.0));
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }
}
