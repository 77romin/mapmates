package com.ssafy.mapmates.common;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization: Bearer 토큰을 해석해 request attribute(loginUser)에 담는다.
 * GET 조회는 비로그인 허용, 그 외 변경 요청과 /me 경로는 로그인 필수.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    public static final String ATTR = "loginUser";
    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
        String auth = req.getHeader("Authorization");
        LoginUser user = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            user = jwtUtil.parse(auth.substring(7));
        }
        if (user != null) req.setAttribute(ATTR, user);

        String uri = req.getRequestURI();
        boolean open = uri.startsWith("/api/auth/") || uri.startsWith("/api/ai/") || uri.startsWith("/api/news");
        boolean needLogin = !open && (!"GET".equalsIgnoreCase(req.getMethod())
                || uri.contains("/me") || uri.startsWith("/api/plans"));
        if (needLogin && user == null) {
            throw ApiException.unauthorized("로그인이 필요합니다.");
        }
        return true;
    }

    public static LoginUser get(HttpServletRequest req) {
        return (LoginUser) req.getAttribute(ATTR);
    }

    public static LoginUser require(HttpServletRequest req) {
        LoginUser u = get(req);
        if (u == null) throw ApiException.unauthorized("로그인이 필요합니다.");
        return u;
    }
}
