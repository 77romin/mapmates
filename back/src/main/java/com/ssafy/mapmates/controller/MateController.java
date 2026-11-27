package com.ssafy.mapmates.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.common.AuthInterceptor;
import com.ssafy.mapmates.dto.MatePost;
import com.ssafy.mapmates.service.MateService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 메이트 모집 / 참여 신청 / 수락 */
@RestController
@RequestMapping("/api/mates")
@RequiredArgsConstructor
public class MateController {
    private final MateService mateService;

    @GetMapping
    public List<MatePost> list(HttpServletRequest req) {
        return mateService.list(AuthInterceptor.get(req));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable int id, HttpServletRequest req) {
        return mateService.detail(id, AuthInterceptor.get(req));
    }

    @PostMapping
    public MatePost create(@RequestBody MatePost post, HttpServletRequest req) {
        return mateService.create(post, AuthInterceptor.require(req));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable int id, HttpServletRequest req) {
        mateService.delete(id, AuthInterceptor.require(req));
        return Map.of("message", "삭제되었습니다.");
    }

    @PostMapping("/{id}/apply")
    public Map<String, Object> apply(@PathVariable int id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        mateService.apply(id, body == null ? null : body.get("message"), AuthInterceptor.require(req));
        return Map.of("message", "참여 신청이 완료되었습니다.");
    }

    @PostMapping("/applications/{applicationId}/{decision}")
    public Map<String, Object> decide(@PathVariable int applicationId, @PathVariable String decision, HttpServletRequest req) {
        mateService.decide(applicationId, "accept".equals(decision), AuthInterceptor.require(req));
        return Map.of("message", "처리되었습니다.");
    }
}
