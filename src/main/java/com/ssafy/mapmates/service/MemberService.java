package com.ssafy.mapmates.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.common.JwtUtil;
import com.ssafy.mapmates.common.LoginUser;
import com.ssafy.mapmates.common.PasswordUtil;
import com.ssafy.mapmates.dao.MemberDao;
import com.ssafy.mapmates.dto.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberDao memberDao;
    private final JwtUtil jwtUtil;

    public Member join(Member m) {
        if (isBlank(m.getUserId()) || isBlank(m.getPassword()) || isBlank(m.getName()) || isBlank(m.getEmail())) {
            throw ApiException.badRequest("아이디, 비밀번호, 이름, 이메일은 필수입니다.");
        }
        if (!m.getUserId().matches("[A-Za-z0-9_]{4,20}")) throw ApiException.badRequest("아이디는 영문·숫자 4~20자입니다.");
        if (m.getPassword().length() < 8) throw ApiException.badRequest("비밀번호는 8자 이상이어야 합니다.");
        if (memberDao.findByUserId(m.getUserId()) != null) throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        m.setPassword(PasswordUtil.hash(m.getPassword()));
        m.setRole("USER");
        memberDao.insert(m);
        return memberDao.findById(m.getId());
    }

    public Map<String, Object> login(String userId, String password) {
        Member m = memberDao.findByUserId(userId);
        if (m == null || !PasswordUtil.matches(password, m.getPassword())) {
            throw ApiException.unauthorized("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        String token = jwtUtil.create(new LoginUser(m.getId(), m.getUserId(), m.getRole()));
        return Map.of("token", token, "member", m);
    }

    public Member get(int id) {
        Member m = memberDao.findById(id);
        if (m == null) throw ApiException.notFound("회원을 찾을 수 없습니다.");
        return m;
    }

    public Member update(int id, Member req) {
        Member m = get(id);
        if (!isBlank(req.getName())) m.setName(req.getName());
        if (!isBlank(req.getEmail())) m.setEmail(req.getEmail());
        m.setTags(req.getTags());
        m.setHomeLat(req.getHomeLat());
        m.setHomeLng(req.getHomeLng());
        memberDao.update(m);
        if (!isBlank(req.getPassword())) {
            if (req.getPassword().length() < 8) throw ApiException.badRequest("비밀번호는 8자 이상이어야 합니다.");
            memberDao.updatePassword(id, PasswordUtil.hash(req.getPassword()));
        }
        return memberDao.findById(id);
    }

    public void withdraw(int id) {
        memberDao.delete(id);
    }

    /** 비밀번호 찾기: 아이디+이메일이 일치하면 임시 비밀번호로 재설정한다. (메일 서버가 없어 응답으로 전달) */
    public String resetPassword(String userId, String email) {
        Member m = memberDao.findByUserId(userId);
        if (m == null || email == null || !email.equalsIgnoreCase(m.getEmail())) {
            throw ApiException.notFound("일치하는 회원 정보가 없습니다.");
        }
        String temp = PasswordUtil.randomPassword();
        memberDao.updatePassword(m.getId(), PasswordUtil.hash(temp));
        return temp;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
