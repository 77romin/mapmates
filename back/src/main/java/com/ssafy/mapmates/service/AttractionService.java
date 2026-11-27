package com.ssafy.mapmates.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ssafy.mapmates.algorithm.GeoUtil;
import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.dao.AttractionDao;
import com.ssafy.mapmates.dto.Attraction;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttractionService {
    private final AttractionDao attractionDao;
    private final InfoService infoService;
    /** 관광공사 API 에서도 사진을 찾지 못한 장소. 같은 장소를 반복 조회하지 않기 위해 기억한다. */
    private final Set<Integer> noImage = ConcurrentHashMap.newKeySet();
    /** 원본 API 에도 소개 글이 없던 장소 (다시 묻지 않기 위함) */
    private final Set<Integer> noOverview = ConcurrentHashMap.newKeySet();

    public List<Map<String, Object>> sidos() { return attractionDao.sidos(); }
    public List<Map<String, Object>> guguns(int sidoCode) { return attractionDao.guguns(sidoCode); }
    public List<Map<String, Object>> contentTypes() { return attractionDao.contentTypes(); }

    public Map<String, Object> search(Integer sidoCode, Integer gugunCode, Integer contentTypeId, String keyword, int page, int size) {
        return search(sidoCode, gugunCode, contentTypeId, keyword, null, page, size);
    }

    /** box = {minLat, maxLat, minLng, maxLng}. 지도에 보이는 영역 안에서만 찾을 때 준다. (이 경우 시도·구군 조건은 쓰지 않는다) */
    public Map<String, Object> search(Integer sidoCode, Integer gugunCode, Integer contentTypeId, String keyword, double[] box, int page, int size) {
        Double minLat = box == null ? null : box[0], maxLat = box == null ? null : box[1];
        Double minLng = box == null ? null : box[2], maxLng = box == null ? null : box[3];
        if (box != null) {
            sidoCode = null;
            gugunCode = null;
        }
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(1, page);
        Map<String, Object> result = new HashMap<>();
        result.put("items", attractionDao.search(sidoCode, gugunCode, contentTypeId, keyword, minLat, maxLat, minLng, maxLng, (page - 1) * size, size));
        result.put("total", attractionDao.count(sidoCode, gugunCode, contentTypeId, keyword, minLat, maxLat, minLng, maxLng));
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Attraction get(int no) {
        Attraction a = attractionDao.findByNo(no);
        if (a == null) throw ApiException.notFound("관광지를 찾을 수 없습니다.");
        return a;
    }

    /**
     * 소개 글이 비어 있으면 한국관광공사 API 에서 찾아 채우고 DB 에 저장한다. (다음부터는 바로 쓰인다)
     * 찾지 못한 장소는 기억해 두고 다시 묻지 않는다. 채웠으면 true.
     */
    public boolean fillOverview(Attraction a) {
        if (a == null || a.getContentId() == null || noOverview.contains(a.getNo())) return false;
        if (a.getOverview() != null && !a.getOverview().isBlank()) return false;
        String overview = infoService.tourOverview(a.getContentId());
        if (overview == null) {
            noOverview.add(a.getNo());
            return false;
        }
        attractionDao.updateOverview(a.getNo(), overview);
        a.setOverview(overview);
        return true;
    }

    /**
     * 상세 조회. DB 에 대표 사진이 없으면 한국관광공사 API 에서 찾아 보여주고,
     * 찾은 주소는 DB 에 저장해 다음부터는 목록에서도 바로 쓰이게 한다.
     */
    public Attraction getDetail(int no) {
        Attraction a = get(no);
        boolean missing = a.getFirstImage1() == null || a.getFirstImage1().isBlank();
        if (missing && a.getContentId() != null && !noImage.contains(no)) {
            String url = infoService.tourImage(a.getContentId());
            if (url == null) {
                noImage.add(no);
            } else {
                attractionDao.updateImage(no, url);
                a.setFirstImage1(url);
            }
        }
        fillOverview(a);
        return a;
    }

    public List<Attraction> findByNos(List<Integer> nos) {
        if (nos == null || nos.isEmpty()) return List.of();
        Map<Integer, Attraction> map = new HashMap<>();
        attractionDao.findByNos(nos).forEach(a -> map.put(a.getNo(), a));
        return nos.stream().map(map::get).filter(a -> a != null).toList(); // 요청 순서 유지
    }

    /**
     * 주변 검색: 반경을 위경도 사각형으로 바꿔 인덱스로 후보를 줄이고(필터),
     * Haversine 으로 정확한 거리를 계산해 가까운 순으로 정렬한다(정제).
     */
    public List<Attraction> nearby(double lat, double lng, double radiusKm, List<Integer> types, int limit) {
        double dLat = GeoUtil.latDelta(radiusKm), dLng = GeoUtil.lngDelta(radiusKm, lat);
        List<Attraction> list = attractionDao.findInBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng, types);
        list.forEach(a -> a.setDistance(Math.round(GeoUtil.distanceKm(lat, lng, a.getLatitude(), a.getLongitude()) * 100) / 100.0));
        return list.stream().filter(a -> a.getDistance() <= radiusKm && a.getDistance() > 0.001)
                .sorted(Comparator.comparingDouble(Attraction::getDistance)).limit(limit).toList();
    }

    /**
     * AI 코스 추천용 후보 묶음.
     * 무작위로 기준 장소 하나를 고르고 그 주변(radiusKm)에서 후보를 모아, 매번 다른 지역의 동선이 가까운 조합이 나오게 한다.
     */
    public List<Attraction> candidates(Integer sidoCode, List<Integer> types, String keyword, double radiusKm, int poolSize) {
        List<Attraction> anchors = attractionDao.findRandom(sidoCode, List.of(12, 14), keyword, 1);
        if (anchors.isEmpty()) anchors = attractionDao.findRandom(sidoCode, types, keyword, 1);
        if (anchors.isEmpty()) return List.of();
        return candidatesNear(anchors.get(0), sidoCode, types, radiusKm, poolSize);
    }

    /** 이름으로 실제 장소를 찾는다. 없으면 null. (AI 가 추천한 장소가 DB 에 있는지 확인하는 용도) */
    public Attraction findByName(Integer sidoCode, String name) {
        if (name == null || name.isBlank() || name.trim().length() < 2) return null;
        return attractionDao.findBestByName(sidoCode, name.trim());
    }

    /**
     * 이름으로 후보 여러 곳을 찾는다. 그대로 포함된 이름이 없으면 글자를 두 자씩 끊어 느슨하게 찾는다.
     * (예: "남산타워" -> %남산%타워% -> "남산서울타워")
     */
    public List<Attraction> findCandidatesByName(Integer sidoCode, String name, int size) {
        if (name == null || name.trim().length() < 2) return List.of();
        String n = name.trim().replace("%", "").replace("_", "");
        List<Attraction> found = attractionDao.findCandidatesByName(sidoCode, n, "%" + n + "%", size);
        String compact = n.replace(" ", "");
        if (found.isEmpty() && compact.length() >= 4) {
            StringBuilder loose = new StringBuilder("%");
            for (int i = 0; i < compact.length(); i += 2) loose.append(compact, i, Math.min(i + 2, compact.length())).append('%');
            found = attractionDao.findCandidatesByName(sidoCode, n, loose.toString(), size);
        }
        return found;
    }

    /** 기준 장소 주변의 후보 묶음 */
    public List<Attraction> candidatesNear(Attraction anchor, Integer sidoCode, List<Integer> types, double radiusKm, int poolSize) {
        List<Attraction> around = new ArrayList<>(nearby(anchor.getLatitude(), anchor.getLongitude(), radiusKm, types, 300).stream()
                .filter(a -> a.getFirstImage1() != null && !a.getFirstImage1().isBlank())
                .filter(a -> sidoCode == null || sidoCode.equals(a.getAreaCode())).toList());
        Collections.shuffle(around);
        // 한 유형이 후보를 독차지하지 않도록 유형별로 고르게 담는다
        int perType = Math.max(2, poolSize / Math.max(1, types == null ? 1 : types.size()));
        Map<Integer, Integer> used = new HashMap<>();
        List<Attraction> pool = new ArrayList<>();
        if (types == null || types.contains(anchor.getContentTypeId())) {
            pool.add(anchor);
            used.merge(anchor.getContentTypeId(), 1, Integer::sum);
        }
        for (Attraction a : around) {
            if (pool.size() >= poolSize) break;
            if (used.getOrDefault(a.getContentTypeId(), 0) >= perType) continue;
            pool.add(a);
            used.merge(a.getContentTypeId(), 1, Integer::sum);
        }
        if (pool.size() < 4) { // 주변에 후보가 부족하면 지역 전체에서 뽑는다
            pool = new ArrayList<>(attractionDao.findRandom(sidoCode, types, null, poolSize));
        }
        return pool;
    }

    public Integer findSidoCode(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.replaceAll("(특별시|광역시|특별자치시|특별자치도|도|시)$", "");
        return attractionDao.findSidoCode(n.isBlank() ? name : n);
    }
}
