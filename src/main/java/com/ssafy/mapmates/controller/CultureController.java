package com.ssafy.mapmates.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.dto.Attraction;
import com.ssafy.mapmates.service.AttractionService;
import com.ssafy.mapmates.service.CultureService;

import lombok.RequiredArgsConstructor;

/** 공연(KOPIS)·서울시 문화행사 조회 + 공연장/행사장 주변 관광지 */
@RestController
@RequestMapping("/api/culture")
@RequiredArgsConstructor
public class CultureController {
    private final CultureService cultureService;
    private final AttractionService attractionService;

    @GetMapping("/performances")
    public Map<String, Object> performances(@RequestParam(required = false) Integer sidoCode,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return cultureService.performances(sidoCode, keyword, page, size);
    }

    @GetMapping("/performances/{id}")
    public Map<String, Object> performance(@PathVariable String id) {
        return cultureService.performance(id);
    }

    @GetMapping("/facilities/{id}")
    public Map<String, Object> facility(@PathVariable String id) {
        return cultureService.facility(id);
    }

    @GetMapping("/seoul-events")
    public Map<String, Object> seoulEvents(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return cultureService.seoulEvents(keyword, page, size);
    }

    /** 공연장·행사장 좌표 주변의 관광지·문화시설·음식점 (플랜에 함께 담을 장소 추천) */
    @GetMapping("/nearby")
    public List<Attraction> nearby(@RequestParam double latitude, @RequestParam double longitude,
            @RequestParam(defaultValue = "2") double radius) {
        return attractionService.nearby(latitude, longitude, Math.min(radius, 10), List.of(12, 14, 39), 10);
    }
}
