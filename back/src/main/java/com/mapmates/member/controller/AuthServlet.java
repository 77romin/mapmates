package com.mapmates.member.controller;

import com.mapmates.common.security.SessionUser;
import com.mapmates.common.web.ApiRequest;
import com.mapmates.common.web.BaseApiServlet;
import com.mapmates.member.dto.LoginRequest;
import com.mapmates.member.dto.MemberResponse;
import com.mapmates.member.dto.PasswordResetRequest;
import com.mapmates.member.service.MemberService;
import com.mapmates.member.service.MemberServiceImpl;
import jakarta.servlet.annotation.WebServlet;

import java.util.LinkedHashMap;
import java.util.Map;

/** 로그인 관리 (F108): /api/auth/* */
@WebServlet("/api/auth/*")
public class AuthServlet extends BaseApiServlet {

    private final MemberService memberService = MemberServiceImpl.getInstance();

    @Override
    protected void routes(Routes r) {
        r.post("/login", this::login);
        r.post("/logout", this::logout);
        r.get("/me", this::me);
        r.post("/password-reset/request",q->{com.mapmates.common.security.RequestLimits.check("recovery-ip:"+q.raw().getRemoteAddr(),20,900000);com.mapmates.member.service.PasswordRecovery.start(q.body(com.mapmates.member.service.PasswordRecovery.Start.class).email());return Map.of("message","가입된 이메일이라면 재설정 메일을 보내드렸어요.");});
        r.post("/password-reset",q->{com.mapmates.common.security.RequestLimits.check("reset-ip:"+q.raw().getRemoteAddr(),30,900000);var body=q.body(com.mapmates.member.service.PasswordRecovery.Finish.class);com.mapmates.member.service.PasswordRecovery.finish(body.token(),body.newPassword());q.logout();return null;});
    }

    private Object login(ApiRequest req) {
        var body=req.body(LoginRequest.class);
        com.mapmates.common.security.RequestLimits.check("login-ip:"+req.raw().getRemoteAddr(),100,900000);
        com.mapmates.common.security.RequestLimits.check("login-account:"+com.mapmates.member.service.PasswordRecovery.hash(body.email()==null?"":body.email().trim().toLowerCase(java.util.Locale.ROOT)),20,900000);
        MemberResponse member = memberService.login(body);
        req.login(new SessionUser(member.id(), member.email(), member.nickname(), member.role()));
        return member;
    }

    private Object logout(ApiRequest req) {
        req.logout();
        return null;
    }

    /** 로그인하지 않아도 200으로 {loggedIn:false}를 준다. 프론트가 첫 화면에서 상태를 확인할 때 쓴다. */
    private Object me(ApiRequest req) {
        SessionUser user = req.loginUser();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loggedIn", user != null);
        body.put("member", user == null ? null : memberService.getMe(user.id()));
        return body;
    }

    private Object resetPassword(ApiRequest req) {
        memberService.resetPassword(req.body(PasswordResetRequest.class));
        return null;
    }
}
