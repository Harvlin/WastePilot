package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.auth.AuthResponse;
import com.project.wastepilot.domain.dto.auth.ForgotPasswordRequest;
import com.project.wastepilot.domain.dto.auth.LoginRequest;
import com.project.wastepilot.domain.dto.auth.MessageResponse;
import com.project.wastepilot.domain.dto.auth.RefreshTokenRequest;
import com.project.wastepilot.domain.dto.auth.ResetPasswordRequest;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
    return authService.signup(request);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody SignupRequest request) {
    return authService.signup(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return authService.refreshToken(request.refreshToken());
  }

  @GetMapping("/me")
  public AuthResponse.UserSession me() {
    return authService.getCurrentUser();
  }

  /**
   * POST /api/v1/auth/forgot-password
   * Always returns 200 regardless of whether the email exists (prevents user enumeration).
   */
  @PostMapping("/forgot-password")
  public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return new MessageResponse(
        "If that email is registered, a password reset link has been sent.");
  }

  /**
   * POST /api/v1/auth/reset-password
   * Validates the token, applies the new password, and marks the token as used.
   */
  @PostMapping("/reset-password")
  public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return new MessageResponse("Password has been reset successfully. You may now sign in.");
  }
}
