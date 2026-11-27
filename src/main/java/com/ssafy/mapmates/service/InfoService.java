package com.ssafy.mapmates.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.ssafy.mapmates.algorithm.GeoUtil;
import com.ssafy.mapmates.algorithm.SunCalculator;

import lombok.extern.slf4j.Slf4j;

/** 관광지 부가정보: 날씨(기상청 단기예보), 일출·일몰(계산), 전기차 충전소(한국환경공단) */
@Service
@Slf4j
public class InfoService {
    private final RestClient restClient = RestClient.create();
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>(); // key -> {만료시각, 값}

    @Value("${datago.service-key:}")
    private String serviceKey;

    /** TourAPI 시도코드 -> 행정구역 시도코드(충전소 API zcode) */
    private static final Map<Integer, String> ZCODE = Map.ofEntries(
            Map.entry(1, "11"), Map.entry(2, "28"), Map.entry(3, "30"), Map.entry(4, "27"), Map.entry(5, "29"),
            Map.entry(6, "26"), Map.entry(7, "31"), Map.entry(8, "36"), Map.entry(31, "41"), Map.entry(32, "51"),
            Map.entry(33, "43"), Map.entry(34, "44"), Map.entry(35, "47"), Map.entry(36, "48"), Map.entry(37, "52"),
            Map.entry(38, "46"), Map.entry(39, "50"));

    public SunCalculator.SunTimes sun(double lat, double lng, LocalDate date) {
        return SunCalculator.calc(lat, lng, date == null ? LocalDate.now() : date);
    }

    /** 3시간 간격 단기예보. 키가 없거나 호출 실패 시 available=false 로 응답한다. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> weather(double lat, double lng) {
        int[] grid = toGrid(lat, lng);
        String cacheKey = "w:" + grid[0] + ":" + grid[1];
        Object cached = fromCache(cacheKey);
        if (cached != null) return (Map<String, Object>) cached;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", false);
        if (serviceKey == null || serviceKey.isBlank()) {
            result.put("message", "공공데이터포털 키가 설정되지 않았습니다.");
            return result;
        }
        try {
            // 발표 시각: 02,05,...,23시 (발표 후 약 10분 뒤 제공)
            LocalDateTime base = LocalDateTime.now().minusMinutes(15);
            int h = base.getHour();
            int baseHour = h < 2 ? 23 : ((h - 2) / 3) * 3 + 2;
            if (h < 2) base = base.minusDays(1);
            String url = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=" + key()
                    + "&pageNo=1&numOfRows=300&dataType=JSON&base_date=" + base.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&base_time=" + String.format("%02d00", baseHour) + "&nx=" + grid[0] + "&ny=" + grid[1];
            Map<String, Object> body = restClient.get().uri(URI.create(url)).retrieve().body(Map.class);
            List<Map<String, Object>> items = findItems(body);
            Map<String, Map<String, Object>> byTime = new LinkedHashMap<>();
            for (Map<String, Object> it : items) {
                String cat = String.valueOf(it.get("category"));
                if (!List.of("TMP", "SKY", "PTY", "POP").contains(cat)) continue;
                String time = it.get("fcstDate") + " " + it.get("fcstTime");
                byTime.computeIfAbsent(time, t -> new LinkedHashMap<>(Map.of("time", t))).put(cat, it.get("fcstValue"));
            }
            List<Map<String, Object>> forecasts = new ArrayList<>();
            int i = 0;
            for (Map<String, Object> f : byTime.values()) {
                if (i++ % 3 != 0) continue; // 3시간 간격
                f.put("summary", summary(String.valueOf(f.get("SKY")), String.valueOf(f.get("PTY"))));
                forecasts.add(f);
                if (forecasts.size() >= 8) break;
            }
            result.put("available", !forecasts.isEmpty());
            result.put("forecasts", forecasts);
            if (forecasts.isEmpty()) result.put("message", "예보 데이터를 받지 못했습니다.");
            else toCache(cacheKey, result, 30);
        } catch (Exception e) {
            log.warn("날씨 조회 실패: {}", e.getClass().getSimpleName());
            result.put("message", "날씨 정보를 불러오지 못했습니다.");
        }
        return result;
    }

    /** 기준 좌표 반경 내 전기차 충전소 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chargers(double lat, double lng, Integer areaCode, double radiusKm) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", false);
        String zcode = areaCode == null ? null : ZCODE.get(areaCode);
        if (serviceKey == null || serviceKey.isBlank() || zcode == null) {
            result.put("message", zcode == null ? "지역 정보가 없어 조회할 수 없습니다." : "공공데이터포털 키가 설정되지 않았습니다.");
            return result;
        }
        try {
            String cacheKey = "ev:" + zcode;
            List<Map<String, Object>> all = (List<Map<String, Object>>) fromCache(cacheKey);
            if (all == null) {
                String url = "https://apis.data.go.kr/B552584/EvCharger/getChargerInfo?serviceKey=" + key()
                        + "&pageNo=1&numOfRows=9999&dataType=JSON&zcode=" + zcode;
                Map<String, Object> body = restClient.get().uri(URI.create(url)).retrieve().body(Map.class);
                all = findItems(body);
                if (!all.isEmpty()) toCache(cacheKey, all, 20);
            }
            Map<String, Map<String, Object>> stations = new LinkedHashMap<>();
            for (Map<String, Object> c : all) {
                try {
                    double cLat = Double.parseDouble(String.valueOf(c.get("lat")));
                    double cLng = Double.parseDouble(String.valueOf(c.get("lng")));
                    double dist = GeoUtil.distanceKm(lat, lng, cLat, cLng);
                    if (dist > radiusKm) continue;
                    String statId = String.valueOf(c.get("statId"));
                    Map<String, Object> s = stations.computeIfAbsent(statId, k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", c.get("statNm"));
                        m.put("addr", c.get("addr"));
                        m.put("latitude", cLat);
                        m.put("longitude", cLng);
                        m.put("distance", Math.round(dist * 100) / 100.0);
                        m.put("useTime", c.get("useTime"));
                        m.put("total", 0);
                        m.put("availableCount", 0);
                        return m;
                    });
                    s.put("total", (int) s.get("total") + 1);
                    if ("2".equals(String.valueOf(c.get("stat")))) s.put("availableCount", (int) s.get("availableCount") + 1); // 2: 충전대기
                } catch (NumberFormatException ignore) {
                    // 좌표 없는 데이터는 건너뛴다
                }
            }
            List<Map<String, Object>> list = new ArrayList<>(stations.values());
            list.sort(Comparator.comparingDouble(m -> (double) m.get("distance")));
            result.put("available", true);
            result.put("stations", list.stream().limit(20).toList());
        } catch (Exception e) {
            log.warn("충전소 조회 실패: {}", e.getClass().getSimpleName());
            result.put("message", "충전소 정보를 불러오지 못했습니다.");
        }
        return result;
    }

    private String key() {
        // 인코딩된 키(%)는 그대로, 디코딩 키는 인코딩해서 사용
        return serviceKey.contains("%") ? serviceKey : URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
    }

    /** 응답 JSON 안에서 item 배열을 찾아낸다. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findItems(Object node) {
        if (node instanceof Map<?, ?> map) {
            Object item = map.get("item");
            if (item instanceof List<?> l) return (List<Map<String, Object>>) l;
            for (Object v : map.values()) {
                List<Map<String, Object>> found = findItems(v);
                if (!found.isEmpty()) return found;
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                List<Map<String, Object>> found = findItems(v);
                if (!found.isEmpty()) return found;
            }
        }
        return List.of();
    }

    private String summary(String sky, String pty) {
        return switch (pty) {
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            default -> switch (sky) {
                case "1" -> "맑음";
                case "3" -> "구름많음";
                case "4" -> "흐림";
                default -> "-";
            };
        };
    }

    private Object fromCache(String key) {
        Object[] e = cache.get(key);
        return e != null && (long) e[0] > System.currentTimeMillis() ? e[1] : null;
    }

    private void toCache(String key, Object value, int minutes) {
        cache.put(key, new Object[] { System.currentTimeMillis() + minutes * 60_000L, value });
    }

    /** 위경도 -> 기상청 격자(LCC DFS 좌표) 변환 */
    static int[] toGrid(double lat, double lng) {
        double RE = 6371.00877, GRID = 5.0, SLAT1 = 30.0, SLAT2 = 60.0, OLON = 126.0, OLAT = 38.0, XO = 43, YO = 136;
        double DEGRAD = Math.PI / 180.0;
        double re = RE / GRID, slat1 = SLAT1 * DEGRAD, slat2 = SLAT2 * DEGRAD, olon = OLON * DEGRAD, olat = OLAT * DEGRAD;
        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);
        double ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lng * DEGRAD - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;
        return new int[] { (int) Math.floor(ra * Math.sin(theta) + XO + 0.5), (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5) };
    }
}
