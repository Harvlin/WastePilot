package com.project.wastepilot.domain.dto.auth;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    UserSession user
) {
  public record UserSession(
      String id,
      String fullName,
      String email,
      String role
  ) {
  }
}
