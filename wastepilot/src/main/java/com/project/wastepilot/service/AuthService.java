package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.auth.AuthResponse;
import com.project.wastepilot.domain.dto.auth.LoginRequest;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  private record LoginAttemptState(int failedAttempts, Instant lockUntil) {}

  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final SettingsService settingsService;
  private final ConcurrentHashMap<String, LoginAttemptState> loginAttempts = new ConcurrentHashMap<>();

  @Transactional
  public AuthResponse signup(SignupRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    if (authUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
      throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already registered.");
    }

    AuthUserEntity user = new AuthUserEntity();
    user.setFullName(request.fullName().trim());
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    AuthUserEntity saved = authUserRepository.save(user);
    settingsService.createDefaultSettingsForUser(saved);

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    ensureLoginAllowed(normalizedEmail);
    AuthUserEntity user = authUserRepository.findByEmailIgnoreCase(normalizedEmail)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is invalid."));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      registerFailedAttempt(normalizedEmail);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is invalid.");
    }

    loginAttempts.remove(normalizedEmail);
    return toResponse(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse.UserSession getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required.");
    }

    String subject = null;
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      subject = jwt.getSubject();
    }
    if (subject == null || subject.isBlank()) {
      subject = authentication.getName();
    }

    java.util.UUID userId;
    try {
      userId = java.util.UUID.fromString(subject);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid authentication subject.");
    }

    AuthUserEntity user = authUserRepository.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    return new AuthResponse.UserSession(user.getId().toString(), user.getFullName(), user.getEmail());
  }

  private AuthResponse toResponse(AuthUserEntity user) {
    String token = jwtService.generateToken(user.getId().toString());
    return new AuthResponse(
        token,
        "Bearer",
        new AuthResponse.UserSession(user.getId().toString(), user.getFullName(), user.getEmail())
    );
  }

  private void ensureLoginAllowed(String email) {
    LoginAttemptState state = loginAttempts.get(email);
    if (state == null || state.lockUntil() == null) {
      return;
    }
    if (state.lockUntil().isAfter(Instant.now())) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOCKED", "Too many failed login attempts. Try again later.");
    }
    loginAttempts.remove(email);
  }

  private void registerFailedAttempt(String email) {
    loginAttempts.compute(email, (key, state) -> {
      int nextAttempts = state == null ? 1 : state.failedAttempts() + 1;
      if (nextAttempts >= MAX_FAILED_ATTEMPTS) {
        return new LoginAttemptState(nextAttempts, Instant.now().plus(LOCK_DURATION));
      }
      return new LoginAttemptState(nextAttempts, null);
    });
  }
}
