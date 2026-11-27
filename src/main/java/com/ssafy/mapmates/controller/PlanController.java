package com.ssafy.mapmates.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.common.AuthInterceptor;
import com.ssafy.mapmates.dto.Plan;
import com.ssafy.mapmates.service.PlanService;
import com.ssafy.mapmates.service.RouteService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 여행 계획(F104) + 동선 최적화 + 중간 집결지 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {
    private final PlanService planService;
    private final RouteService routeService;

    @GetMapping
    public List<Plan> mine(HttpServletRequest req) {
        return planService.mine(AuthInterceptor.require(req).id());
    }

    @GetMapping("/{id}")
    public Plan get(@PathVariable int id, HttpServletRequest req) {
        return planService.get(id, AuthInterceptor.get(req));
    }

    @PostMapping
    public Plan create(@RequestBody Plan plan, HttpServletRequest req) {
        return planService.create(plan, AuthInterceptor.require(req));
    }

    @PutMapping("/{id}")
    public Plan update(@PathVariable int id, @RequestBody Plan plan, HttpServletRequest req) {
        return planService.update(id, plan, AuthInterceptor.require(req));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable int id, HttpServletRequest req) {
        planService.delete(id, AuthInterceptor.require(req));
        return Map.of("message", "삭제되었습니다.");
    }

    @PostMapping("/optimize")
    public Map<String, Object> optimize(@RequestBody Map<String, List<Integer>> body) {
        return planService.optimize(body.get("attractionNos"));
    }

    /** 이동 수단별 경로. body: { mode: car | walk | transit, attractionNos: [..] } */
    @PostMapping("/route")
    @SuppressWarnings("unchecked")
    public Map<String, Object> route(@RequestBody Map<String, Object> body) {
        Object nos = body.get("attractionNos");
        if (!(nos instanceof List<?> list)) throw com.ssafy.mapmates.common.ApiException.badRequest("attractionNos 가 필요합니다.");
        List<Integer> ids = list.stream().filter(o -> o instanceof Number).map(o -> ((Number) o).intValue()).toList();
        return routeService.route(String.valueOf(body.get("mode")), ids);
    }

    @GetMapping("/{id}/meeting-point")
    public Map<String, Object> meetingPoint(@PathVariable int id, HttpServletRequest req) {
        return planService.meetingPoint(id, AuthInterceptor.require(req));
    }
}
