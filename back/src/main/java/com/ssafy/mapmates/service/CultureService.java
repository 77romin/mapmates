package com.ssafy.mapmates.service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ssafy.mapmates.common.ApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * 공연·문화행사 정보
 * - KOPIS 공연예술통합전산망: 공연 목록 / 공연 상세 / 공연 시설 (XML)
 * - 서울 열린데이터 광장: 서울시 문화행사 정보 (JSON)
 */
@Service
@Slf4j
public class CultureService {
    private static final String KOPIS = "http://www.kopis.or.kr/openApi/restful";
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    /** TourAPI 시도코드 -> KOPIS 지역코드(행정구역 시도코드) */
    private static final Map<Integer, String> SIGNGU = Map.ofEntries(
            Map.entry(1, "11"), Map.entry(2, "28"), Map.entry(3, "30"), Map.entry(4, "27"), Map.entry(5, "29"),
            Map.entry(6, "26"), Map.entry(7, "31"), Map.entry(8, "36"), Map.entry(31, "41"), Map.entry(32, "51"),
            Map.entry(33, "43"), Map.entry(34, "44"), Map.entry(35, "47"), Map.entry(36, "48"), Map.entry(37, "45"),
            Map.entry(38, "46"), Map.entry(39, "50"));

    private final RestClient restClient = RestClient.builder().requestFactory(timeoutFactory()).build();
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();

    @Value("${kopis.service-key:}")
    private String kopisKey;
    @Value("${seoul.api-key:}")
    private String seoulKey;

    /** 공연 목록: 오늘부터 30일 이내 공연 중·예정인 공연 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> performances(Integer sidoCode, String keyword, int page, int size) {
        requireKey(kopisKey, "KOPIS");
        size = Math.max(1, Math.min(size, 50));
        page = Math.max(1, page);
        LocalDate today = LocalDate.now();
        StringBuilder url = new StringBuilder(KOPIS).append("/pblprfr?service=").append(enc(kopisKey))
                .append("&stdate=").append(today.format(YMD)).append("&eddate=").append(today.plusDays(30).format(YMD))
                .append("&cpage=").append(page).append("&rows=").append(size);
        String signgu = sidoCode == null ? null : SIGNGU.get(sidoCode);
        if (signgu != null) url.append("&signgucode=").append(signgu);
        if (keyword != null && !keyword.isBlank()) url.append("&shprfnm=").append(enc(keyword.trim()));

        String cacheKey = url.toString();
        Object cached = fromCache(cacheKey);
        if (cached != null) return (Map<String, Object>) cached;

        List<Map<String, String>> items = new ArrayList<>();
        for (Element db : fetchXml(cacheKey)) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", text(db, "mt20id"));
            m.put("title", text(db, "prfnm"));
            m.put("startDate", text(db, "prfpdfrom"));
            m.put("endDate", text(db, "prfpdto"));
            m.put("venue", text(db, "fcltynm"));
            m.put("poster", https(text(db, "poster")));
            m.put("area", text(db, "area"));
            m.put("genre", text(db, "genrenm"));
            m.put("state", text(db, "prfstate"));
            if (m.get("id") != null) items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("hasNext", items.size() == size);
        toCache(cacheKey, result, 30);
        return result;
    }

    /** 공연 상세 + 공연 시설(주소·좌표) */
    @SuppressWarnings("unchecked")
    public Map<String, Object> performance(String id) {
        requireKey(kopisKey, "KOPIS");
        if (id == null || !id.matches("[A-Za-z0-9]{1,20}")) throw ApiException.badRequest("공연 ID 형식이 올바르지 않습니다.");
        String cacheKey = "prf:" + id;
        Object cached = fromCache(cacheKey);
        if (cached != null) return (Map<String, Object>) cached;

        List<Element> dbs = fetchXml(KOPIS + "/pblprfr/" + id + "?service=" + enc(kopisKey));
        if (dbs.isEmpty() || text(dbs.get(0), "mt20id") == null) throw ApiException.notFound("공연 정보를 찾을 수 없습니다.");
        Element db = dbs.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", text(db, "mt20id"));
        m.put("title", text(db, "prfnm"));
        m.put("startDate", text(db, "prfpdfrom"));
        m.put("endDate", text(db, "prfpdto"));
        m.put("venue", text(db, "fcltynm"));
        m.put("cast", text(db, "prfcast"));
        m.put("runtime", text(db, "prfruntime"));
        m.put("age", text(db, "prfage"));
        m.put("price", text(db, "pcseguidance"));
        m.put("poster", https(text(db, "poster")));
        m.put("genre", text(db, "genrenm"));
        m.put("state", text(db, "prfstate"));
        m.put("schedule", text(db, "dtguidance"));
        String facilityId = text(db, "mt10id");
        if (facilityId != null) m.put("facility", facility(facilityId));
        toCache(cacheKey, m, 60);
        return m;
    }

    /** 공연 시설 상세 */
    public Map<String, Object> facility(String facilityId) {
        Map<String, Object> f = new LinkedHashMap<>();
        if (facilityId == null || !facilityId.matches("[A-Za-z0-9]{1,20}")) return f;
        try {
            List<Element> dbs = fetchXml(KOPIS + "/prfplc/" + facilityId + "?service=" + enc(kopisKey));
            if (dbs.isEmpty()) return f;
            Element db = dbs.get(0);
            f.put("id", facilityId);
            f.put("name", text(db, "fcltynm"));
            f.put("address", text(db, "adres"));
            f.put("tel", text(db, "telno"));
            f.put("seats", text(db, "seatscale"));
            f.put("homepage", text(db, "relateurl"));
            f.put("latitude", number(text(db, "la")));
            f.put("longitude", number(text(db, "lo")));
        } catch (Exception e) {
            log.warn("공연 시설 조회 실패: {}", e.getMessage());
        }
        return f;
    }

    /** 서울시 문화행사: 종료일이 지나지 않은 행사 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> seoulEvents(String keyword, int page, int size) {
        requireKey(seoulKey, "서울 열린데이터 광장");
        size = Math.max(1, Math.min(size, 50));
        page = Math.max(1, page);
        int start = (page - 1) * size + 1;
        String title = keyword == null || keyword.isBlank() ? "%20" : enc(keyword.trim()).replace("+", "%20");
        // 경로: /{KEY}/json/culturalEventInfo/{start}/{end}/{분류}/{공연행사명}
        String url = "http://openapi.seoul.go.kr:8088/" + enc(seoulKey) + "/json/culturalEventInfo/" + start + "/" + (start + size - 1) + "/%20/" + title;
        Object cached = fromCache(url);
        if (cached != null) return (Map<String, Object>) cached;

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int total = 0;
        try {
            Map<String, Object> body = restClient.get().uri(URI.create(url)).retrieve().body(Map.class);
            Object info = body == null ? null : body.get("culturalEventInfo");
            if (info instanceof Map<?, ?> infoMap) {
                total = infoMap.get("list_total_count") instanceof Number n ? n.intValue() : 0;
                if (infoMap.get("row") instanceof List<?> rows) {
                    for (Object o : rows) {
                        Map<String, Object> r = (Map<String, Object>) o;
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("title", r.get("TITLE"));
                        m.put("category", r.get("CODENAME"));
                        m.put("district", r.get("GUNAME"));
                        m.put("period", r.get("DATE"));
                        m.put("place", r.get("PLACE"));
                        m.put("target", r.get("USE_TRGT"));
                        m.put("fee", r.get("USE_FEE"));
                        m.put("isFree", r.get("IS_FREE"));
                        m.put("image", https(str(r.get("MAIN_IMG"))));
                        m.put("homepage", str(r.get("HMPG_ADDR")) == null ? r.get("ORG_LINK") : r.get("HMPG_ADDR"));
                        // 원본 데이터의 LAT/LOT 값이 뒤바뀌어 제공되는 경우가 있어 크기로 위도·경도를 판별한다
                        Double a = number(str(r.get("LAT"))), b = number(str(r.get("LOT")));
                        if (a != null && b != null) {
                            m.put("latitude", Math.min(a, b));
                            m.put("longitude", Math.max(a, b));
                        }
                        items.add(m);
                    }
                }
            }
        } catch (ResourceAccessException e) {
            log.warn("서울 열린데이터 서버 연결 실패");
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "서울 열린데이터 서버에 연결할 수 없습니다. 현재 네트워크에서 접속이 차단되었을 수 있습니다.");
        } catch (Exception e) {
            log.warn("서울 문화행사 조회 실패: {}", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "서울 문화행사 정보를 불러오지 못했습니다.");
        }
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("hasNext", start + size - 1 < total);
        toCache(url, result, 30);
        return result;
    }

    public boolean kopisAvailable() {
        return kopisKey != null && !kopisKey.isBlank();
    }

    /** 외부 서버가 응답하지 않을 때 화면이 오래 멈추지 않도록 연결 5초, 응답 15초로 제한한다. */
    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    // ---------- 내부 유틸 ----------
    /** KOPIS XML 응답의 db 요소 목록 */
    private List<Element> fetchXml(String url) {
        try {
            byte[] xml = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE 방지
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList nodes = doc.getElementsByTagName("db");
            List<Element> list = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) list.add((Element) nodes.item(i));
            return list;
        } catch (Exception e) {
            log.warn("KOPIS 조회 실패: {}", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "공연 정보를 불러오지 못했습니다.");
        }
    }

    /** 직계 자식 요소의 텍스트 (없거나 비어 있으면 null) */
    private String text(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && tag.equals(e.getTagName())) {
                String t = e.getTextContent() == null ? "" : e.getTextContent().trim();
                return t.isEmpty() ? null : t;
            }
        }
        return null;
    }

    private void requireKey(String key, String name) {
        if (key == null || key.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, name + " API 키가 설정되지 않았습니다.");
        }
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String https(String url) {
        return url == null ? null : url.replaceFirst("^http://", "https://");
    }

    private String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }

    private Double number(String s) {
        try {
            return s == null ? null : Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object fromCache(String key) {
        Object[] e = cache.get(key);
        return e != null && (long) e[0] > System.currentTimeMillis() ? e[1] : null;
    }

    private void toCache(String key, Object value, int minutes) {
        cache.put(key, new Object[] { System.currentTimeMillis() + minutes * 60_000L, value });
    }
}
