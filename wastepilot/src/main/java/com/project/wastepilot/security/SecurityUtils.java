package com.project.wastepilot.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {
  public static String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      if (authentication.getPrincipal() instanceof Jwt jwt) {
        return jwt.getSubject(); // standard approach for JWT
      }
      if (authentication.getPrincipal() instanceof String principalStr) {
        return principalStr; // for mock auth fallback
      }
      String name = authentication.getName();
      if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
        return name;
      }
    }
    return "SYSTEM"; // fallback for internal jobs
  }
}
