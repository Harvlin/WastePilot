package com.project.wastepilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window rate limiter for all unauthenticated auth-related endpoints.
 *
 * <p>IP resolution: uses {@code request.getRemoteAddr()} which reflects the real peer IP
 * when Spring's ForwardedHeaderFilter is in the filter chain (configured via
 * {@code server.forward-headers-strategy=framework} in application.properties). This ensures
 * that client-controlled headers such as {@code X-Forwarded-For} cannot be used to bypass
 * per-IP limits.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final int MAX_ATTEMPTS_PER_MINUTE = 30;
  private static final long WINDOW_MS = 60_000L;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Every unauthenticated POST on the auth surface.
   * Refresh is included because an attacker can probe it to enumerate valid tokens.
   * Forgot-password and reset-password are included because they are sensitive
   * credential-recovery endpoints.
   */
  private static final Set<String> RATE_LIMITED_PATHS = Set.of(
      "/api/v1/auth/login",
      "/api/v1/auth/signup",
      "/api/v1/auth/register",
      "/api/v1/auth/refresh",
      "/api/v1/auth/forgot-password",
      "/api/v1/auth/reset-password"
  );

  private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
  private volatile long windowStartMs = System.currentTimeMillis();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    return !RATE_LIMITED_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    rollWindowIfNeeded();
    // getRemoteAddr() returns the real peer after ForwardedHeaderFilter processing.
    // Do NOT use X-Forwarded-For directly here — it is client-controllable.
    String clientIp = request.getRemoteAddr();
    String key = request.getRequestURI() + ":" + clientIp;
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
}
