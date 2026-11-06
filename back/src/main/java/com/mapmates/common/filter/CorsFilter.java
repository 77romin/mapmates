package com.mapmates.common.filter;

import com.mapmates.common.config.AppConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 프론트를 dev-server.js(다른 포트)로 띄웠을 때만 필요하다.
 * WAR에 프론트를 함께 배포하면 같은 출처라 이 필터는 아무 일도 하지 않는다.
 * 세션 쿠키를 쓰므로 허용 목록에 있는 출처만 그대로 돌려준다('*' 사용 불가).
 */
@WebFilter(filterName = "corsFilter", urlPatterns = "/api/*")
public class CorsFilter implements Filter {

    private final Set<String> allowedOrigins = Arrays.stream(AppConfig.get("cors.allowed-origins", "").split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept");
                response.setHeader("Access-Control-Max-Age", "3600");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
