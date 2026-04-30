package com.project.wastepilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final int MAX_ATTEMPTS_PER_MINUTE = 30;
  private static final long WINDOW_MS = 60_000L;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
  private volatile long windowStartMs = System.currentTimeMillis();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    return !("/api/v1/auth/login".equals(path)
        || "/api/v1/auth/signup".equals(path)
        || "/api/v1/auth/register".equals(path));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    rollWindowIfNeeded();
    String key = request.getRequestURI() + ":" + resolveClientIp(request);
    int count = counters.computeIfAbsent(key, ignored -> new AtomicInteger(0)).incrementAndGet();
    if (count > MAX_ATTEMPTS_PER_MINUTE) {
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      OBJECT_MAPPER.writeValue(response.getWriter(), Map.of(
          "timestamp", Instant.now().toString(),
          "status", 429,
          "code", "RATE_LIMITED",
          "message", "Too many authentication attempts. Please try again shortly.",
          "path", request.getRequestURI()
      ));
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void rollWindowIfNeeded() {
    long now = System.currentTimeMillis();
    if (now - windowStartMs <= WINDOW_MS) {
      return;
    }
    synchronized (this) {
      if (now - windowStartMs > WINDOW_MS) {
        counters.clear();
        windowStartMs = now;
      }
    }
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return request.getRemoteAddr();
  }
}
