package com.project.wastepilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Temporary filter to inject a mock user identity into the SecurityContext
 * so that SecurityUtils.getCurrentUserId() works for local development
 * without needing a real OAuth2 JWT setup yet.
 */
public class MockAuthFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    
    // Create a mock authentication token
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        "operator.session", null, List.of()
    );
    
    SecurityContextHolder.getContext().setAuthentication(auth);
    
    filterChain.doFilter(request, response);
  }
}
