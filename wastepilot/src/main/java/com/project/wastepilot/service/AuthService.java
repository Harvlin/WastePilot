package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.auth.AuthResponse;
import com.project.wastepilot.domain.dto.auth.ForgotPasswordRequest;
import com.project.wastepilot.domain.dto.auth.LoginRequest;
import com.project.wastepilot.domain.dto.auth.ResetPasswordRequest;
import com.project.wastepilot.domain.dto.auth.SignupRequest;

public interface AuthService {
  AuthResponse signup(SignupRequest request);
  AuthResponse login(LoginRequest request);
  AuthResponse.UserSession getCurrentUser();
  /**
   * Initiates a password reset. Always returns a generic success message
   * even if the email does not exist, to prevent user enumeration.
   */
  void forgotPassword(ForgotPasswordRequest request);
  void resetPassword(ResetPasswordRequest request);
}
