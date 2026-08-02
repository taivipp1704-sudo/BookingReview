package com.claritycam.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SecurityResponseFilter extends OncePerRequestFilter {
  private static final Set<String> DISABLED_METHODS = Set.of("TRACE", "CONNECT");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (DISABLED_METHODS.contains(request.getMethod())) {
      response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
      response.setHeader(HttpHeaders.ALLOW, "GET, POST, PATCH, DELETE, OPTIONS");
      return;
    }

    if (isSensitiveApi(request.getRequestURI())) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private, max-age=0");
      response.setHeader(HttpHeaders.PRAGMA, "no-cache");
      response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
    filterChain.doFilter(request, response);
  }

  private boolean isSensitiveApi(String path) {
    return path.startsWith("/api/auth/")
        || path.startsWith("/api/otp/")
        || path.startsWith("/api/customer/")
        || path.startsWith("/api/bookings")
        || path.startsWith("/api/admin/")
        || path.startsWith("/api/finance/")
        || path.startsWith("/api/inventory/");
  }
}
