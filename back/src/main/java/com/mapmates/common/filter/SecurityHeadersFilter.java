package com.mapmates.common.filter;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebFilter;import jakarta.servlet.http.*;import java.io.IOException;
@WebFilter("/*") public class SecurityHeadersFilter implements Filter {
 public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException {var r=(HttpServletResponse)response;r.setHeader("Referrer-Policy","no-referrer");r.setHeader("X-Content-Type-Options","nosniff");r.setHeader("X-Frame-Options","DENY");chain.doFilter(request,response);}
}
