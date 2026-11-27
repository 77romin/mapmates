package com.ssafy.mapmates.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.common.AuthInterceptor;
import com.ssafy.mapmates.dto.Member;
import com.ssafy.mapmates.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 회원 관리(F107) / 로그인 관리(F108) */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/auth/join")
    public Member join(@RequestBody Member member) {
        return memberService.join(member);
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        return memberService.login(body.get("userId"), body.get("password"));
    }

    /** 토큰 방식이라 서버 상태는 없지만, 클라이언트 흐름을 위해 로그아웃 엔드포인트를 제공한다. */
    @PostMapping("/auth/logout")
    public Map<String, Object> logout() {
        return Map.of("message", "로그아웃 되었습니다.");
    }

    @PostMapping("/auth/find-password")
    public Map<String, Object> findPassword(@RequestBody Map<String, String> body) {
        String temp = memberService.resetPassword(body.get("userId"), body.get("email"));
        return Map.of("message", "임시 비밀번호가 발급되었습니다. 로그인 후 변경해 주세요.", "tempPassword", temp);
    }

    @GetMapping("/members/me")
    public Member me(HttpServletRequest req) {
        return memberService.get(AuthInterceptor.require(req).id());
    }

    @PutMapping("/members/me")
    public Member update(HttpServletRequest req, @RequestBody Member member) {
        return memberService.update(AuthInterceptor.require(req).id(), member);
    }

    @DeleteMapping("/members/me")
    public Map<String, Object> withdraw(HttpServletRequest req) {
        memberService.withdraw(AuthInterceptor.require(req).id());
        return Map.of("message", "탈퇴 처리되었습니다.");
    }
}
