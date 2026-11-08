package com.mapmates.member.controller;

import com.mapmates.common.exception.ApiException;
import com.mapmates.common.security.SessionUser;
import com.mapmates.common.web.ApiRequest;
import com.mapmates.common.web.BaseApiServlet;
import com.mapmates.member.dto.MemberResponse;
import com.mapmates.member.dto.MemberUpdateRequest;
import com.mapmates.member.dto.PasswordChangeRequest;
import com.mapmates.member.dto.SignupRequest;
import com.mapmates.member.service.MemberService;
import com.mapmates.member.service.MemberServiceImpl;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/** 회원 관리 (F107): /api/members/* */
@WebServlet("/api/members/*")
public class MemberServlet extends BaseApiServlet {

    private final MemberService memberService = MemberServiceImpl.getInstance();

    @Override
    protected void routes(Routes r) {
        // 고정 경로("/me", "/check-email")를 변수 경로("/{id}")보다 먼저 등록해야 한다.
        r.post("/", this::signup);
        r.get("/check-email", this::checkEmail);
        r.get("/check-nickname", this::checkNickname);
        r.get("/me", this::me);
        r.get("/me/activity",q->{long user=q.requireLogin().id();return com.mapmates.common.db.Tx.read(c->Map.of("owned",com.mapmates.common.db.Sql.number(com.mapmates.common.db.Sql.one(c,"SELECT COUNT(*) n FROM companions cp JOIN plans p ON p.id=cp.plan_id WHERE p.owner_id=?",user),"n"),"joined",com.mapmates.common.db.Sql.number(com.mapmates.common.db.Sql.one(c,"SELECT COUNT(*) n FROM companion_participants WHERE member_id=?",user),"n"),"posts",com.mapmates.common.db.Sql.number(com.mapmates.common.db.Sql.one(c,"SELECT COUNT(*) n FROM posts WHERE owner_id=?",user),"n")));});
        r.put("/me", this::update);
        r.delete("/me", this::withdraw);
        r.put("/me/password", this::changePassword);
        r.get("/{id}", this::publicProfile);
    }

    private Object signup(ApiRequest req) {
        if (req.loginUser() != null) {
            throw ApiException.badRequest("이미 로그인되어 있어요. 로그아웃 후 가입해주세요.");
        }
        MemberResponse member = memberService.signup(req.body(SignupRequest.class));
        // 프론트와 같이 가입 직후 바로 로그인 상태가 된다.
        req.login(new SessionUser(member.id(), member.email(), member.nickname(), member.role()));
        req.status(HttpServletResponse.SC_CREATED);
        return member;
    }

    private Object checkEmail(ApiRequest req) {
        return Map.of("available", memberService.isEmailAvailable(req.query("email")));
    }

    private Object checkNickname(ApiRequest req) {
        SessionUser user = req.loginUser();
        return Map.of("available", memberService.isNicknameAvailable(req.query("nickname"), user == null ? null : user.id()));
    }

    private Object me(ApiRequest req) {
        return memberService.getMe(req.requireLogin().id());
    }

    private Object update(ApiRequest req) {
        SessionUser user = req.requireLogin();
        MemberResponse member = memberService.updateProfile(user.id(), req.body(MemberUpdateRequest.class));
        // 닉네임이 바뀌었을 수 있으니 세션 정보도 갱신한다.
        req.raw().getSession().setAttribute(SessionUser.SESSION_KEY,
                new SessionUser(member.id(), member.email(), member.nickname(), member.role()));
        return member;
    }

    private Object changePassword(ApiRequest req) {
        memberService.changePassword(req.requireLogin().id(), req.body(PasswordChangeRequest.class));
        return null;
    }

    private Object withdraw(ApiRequest req) {
        SessionUser user = req.requireLogin();
        memberService.withdraw(user.id(), req.body(PasswordChangeRequest.class).currentPassword());
        req.logout();
        return null;
    }

    private Object publicProfile(ApiRequest req) {
        return memberService.getPublicProfile(req.pathLong("id"));
    }
}
