package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.auth.AuthResponse;
import com.project.wastepilot.domain.dto.auth.LoginRequest;
import com.project.wastepilot.domain.dto.auth.SignupRequest;

public interface AuthService {
    AuthResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse.UserSession getCurrentUser();
}
