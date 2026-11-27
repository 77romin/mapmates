package com.ssafy.mapmates.service;

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

    public List<Map<String, Object>> sidos() { return attractionDao.sidos(); }
    public List<Map<String, Object>> guguns(int sidoCode) { return attractionDao.guguns(sidoCode); }
    public List<Map<String, Object>> contentTypes() { return attractionDao.contentTypes(); }

    public Map<String, Object> search(Integer sidoCode, Integer gugunCode, Integer contentTypeId, String keyword, int page, int size) {
        size = Math.max(1, Math.min(size, 100));
        page = Math.max(1, page);
        Map<String, Object> result = new HashMap<>();
        result.put("items", attractionDao.search(sidoCode, gugunCode, contentTypeId, keyword, (page - 1) * size, size));
        result.put("total", attractionDao.count(sidoCode, gugunCode, contentTypeId, keyword));
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

    public Integer findSidoCode(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.replaceAll("(특별시|광역시|특별자치시|특별자치도|도|시)$", "");
        return attractionDao.findSidoCode(n.isBlank() ? name : n);
    }
}
