package com.mapmates.common.web;

import com.fasterxml.jackson.core.JacksonException;
import com.mapmates.common.exception.ApiException;
import com.mapmates.common.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

/** 핸들러에 전달되는 요청 래퍼. 경로 변수, 쿼리, JSON 본문, 로그인 사용자를 꺼내는 도우미를 제공한다. */
public final class ApiRequest {

    private final HttpServletRequest raw;
    private final HttpServletResponse response;
    private final Map<String, String> pathVariables;
    private int status = HttpServletResponse.SC_OK;

    ApiRequest(HttpServletRequest raw, HttpServletResponse response, Map<String, String> pathVariables) {
        this.raw = raw;
        this.response = response;
        this.pathVariables = pathVariables;
    }

    public HttpServletRequest raw() {
        return raw;
    }

    public HttpServletResponse response() {
        return response;
    }

    // ---------- 경로 변수 / 쿼리 ----------

    public String path(String name) {
        return pathVariables.get(name);
    }

    public long pathLong(String name) {
        try {
            return Long.parseLong(pathVariables.get(name));
        } catch (NumberFormatException e) {
            throw ApiException.notFound("요청한 대상을 찾을 수 없어요.");
        }
    }

    public String query(String name) {
        String value = raw.getParameter(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public int queryInt(String name, int defaultValue, int min, int max) {
        String value = query(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("'" + name + "' 값은 숫자여야 해요.");
        }
    }

    // ---------- 본문 ----------

    public <T> T body(Class<T> type) {
        try {
            T value = JsonUtil.read(raw.getInputStream(), type);
            if (value == null) {
                throw ApiException.badRequest("요청 본문이 비어 있어요.");
            }
            return value;
        } catch (JacksonException e) {
            throw ApiException.badRequest("요청 본문의 JSON 형식이 올바르지 않아요.");
        } catch (IOException e) {
            throw ApiException.badRequest("요청 본문을 읽지 못했어요.");
        }
    }

    // ---------- 로그인 사용자 ----------

    public SessionUser loginUser() {
        HttpSession session = raw.getSession(false);
        if(session==null)return null;
        SessionUser user=(SessionUser)session.getAttribute(SessionUser.SESSION_KEY);if(user==null)return null;
        var current=com.mapmates.common.db.Tx.read(c->com.mapmates.common.db.Sql.one(c,"SELECT auth_version,role FROM members WHERE id=?",user.id()));
        if(current==null||com.mapmates.common.db.Sql.number(current,"auth_version")!=user.authVersion()){session.invalidate();return null;}
        return new SessionUser(user.id(),user.email(),user.nickname(),current.get("role").toString(),user.authVersion());
    }

    public SessionUser requireLogin() {
        SessionUser user = loginUser();
        if (user == null) {
            throw ApiException.unauthorized("로그인이 필요한 기능이에요.");
        }
        return user;
    }

    /** 로그인 성공 시 호출. 세션 고정 공격을 막기 위해 기존 세션을 버리고 새로 만든다. */
    public void login(SessionUser user) {
        HttpSession old = raw.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        long version=com.mapmates.common.db.Tx.read(c->com.mapmates.common.db.Sql.number(com.mapmates.common.db.Sql.one(c,"SELECT auth_version FROM members WHERE id=?",user.id()),"auth_version"));
        raw.getSession(true).setAttribute(SessionUser.SESSION_KEY,new SessionUser(user.id(),user.email(),user.nickname(),user.role(),version));
    }

    public void logout() {
        HttpSession session = raw.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    // ---------- 응답 상태 ----------

    public void status(int status) {
        this.status = status;
    }

    int status() {
        return status;
    }
}
