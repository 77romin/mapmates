package com.mapmates.common.filter;

import com.mapmates.common.config.AppConfig;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;

/** Browser cross-origin writes are rejected even when CORS would hide the response. */
@WebFilter(urlPatterns = "/api/*")
public class WriteSecurityFilter implements Filter {
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    var r = (HttpServletRequest) request;
    var s = (HttpServletResponse) response;
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod())) {
      String origin = r.getHeader("Origin"), own = r.getScheme() + "://" + r.getHeader("Host");
      String configured=AppConfig.get("public.url","");if(configured.startsWith("https://")){var uri=java.net.URI.create(configured);own=uri.getScheme()+"://"+uri.getRawAuthority();}
      var allowed = Arrays.asList(AppConfig.get("cors.allowed-origins", "").split(","));
      boolean invalid = origin != null && !origin.equals(own) && !allowed.contains(origin);
      String type = Objects.toString(r.getContentType(), "").toLowerCase(Locale.ROOT);
      if (invalid
          || ("cross-site".equals(r.getHeader("Sec-Fetch-Site")) && origin == null)
          || (!"DELETE".equals(r.getMethod()) && !type.startsWith("application/json"))) {
        s.setStatus(403);
        s.setContentType("application/json;charset=UTF-8");
        s.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"허용되지 않은 요청이에요.\"}");
        return;
      }
      if (r.getContentLengthLong() > 2_000_000) {
        s.sendError(413);
        return;
      }
    }
    chain.doFilter(r, s);
  }
}
