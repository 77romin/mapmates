package com.ssafy.mapmates.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.algorithm.SunCalculator;
import com.ssafy.mapmates.dto.Attraction;
import com.ssafy.mapmates.service.AttractionService;
import com.ssafy.mapmates.service.InfoService;

import lombok.RequiredArgsConstructor;

/** 관광지 조회(F101~F103) + 부가정보(날씨·일출일몰·충전소·주변 축제/상권) */
@RestController
@RequestMapping("/api/attractions")
@RequiredArgsConstructor
public class AttractionController {
    private final AttractionService attractionService;
    private final InfoService infoService;

    @GetMapping("/sidos")
    public List<Map<String, Object>> sidos() { return attractionService.sidos(); }

    @GetMapping("/guguns")
    public List<Map<String, Object>> guguns(@RequestParam int sidoCode) { return attractionService.guguns(sidoCode); }

    @GetMapping("/content-types")
    public List<Map<String, Object>> contentTypes() { return attractionService.contentTypes(); }

    @GetMapping
    public Map<String, Object> search(@RequestParam(required = false) Integer sidoCode,
            @RequestParam(required = false) Integer gugunCode, @RequestParam(required = false) Integer contentTypeId,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size) {
        return attractionService.search(sidoCode, gugunCode, contentTypeId, keyword, page, size);
    }

    @GetMapping("/{no}")
    public Attraction detail(@PathVariable int no) { return attractionService.getDetail(no); }

    /** 주변 장소. kind: festival(축제·행사 15), store(쇼핑 38·음식점 39), stay(숙박 32), all */
    @GetMapping("/{no}/nearby")
    public List<Attraction> nearby(@PathVariable int no, @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "5") double radius) {
        Attraction a = attractionService.get(no);
        List<Integer> types = switch (kind) {
            case "festival" -> List.of(15);
            case "store" -> List.of(38, 39);
            case "stay" -> List.of(32);
            default -> null;
        };
        return attractionService.nearby(a.getLatitude(), a.getLongitude(), radius, types, 20);
    }

    @GetMapping("/{no}/weather")
    public Map<String, Object> weather(@PathVariable int no) {
        Attraction a = attractionService.get(no);
        return infoService.weather(a.getLatitude(), a.getLongitude());
    }

    @GetMapping("/{no}/sun")
    public SunCalculator.SunTimes sun(@PathVariable int no) {
        Attraction a = attractionService.get(no);
        return infoService.sun(a.getLatitude(), a.getLongitude(), null);
    }

    @GetMapping("/{no}/chargers")
    public Map<String, Object> chargers(@PathVariable int no, @RequestParam(defaultValue = "3") double radius) {
        Attraction a = attractionService.get(no);
        return infoService.chargers(a.getLatitude(), a.getLongitude(), a.getAreaCode(), radius);
    }
}
