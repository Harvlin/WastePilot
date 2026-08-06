package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.auth.AuthResponse;
import com.project.wastepilot.domain.dto.auth.ForgotPasswordRequest;
import com.project.wastepilot.domain.dto.auth.LoginRequest;
import com.project.wastepilot.domain.dto.auth.ResetPasswordRequest;
import com.project.wastepilot.domain.dto.auth.SignupRequest;
import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.domain.entity.PasswordResetTokenEntity;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.PasswordResetTokenRepository;
import com.project.wastepilot.security.JwtService;
import com.project.wastepilot.service.AuthService;
import com.project.wastepilot.service.SettingsService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
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
public class AuthServiceImpl implements AuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  private record LoginAttemptState(int failedAttempts, Instant lockUntil) {}

  private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
  private static final int TOKEN_BYTES = 32;

  private final AuthUserRepository authUserRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final SettingsService settingsService;
  private final ConcurrentHashMap<String, LoginAttemptState> loginAttempts = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
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

  @Override
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

  @Override
  @Transactional(readOnly = true)
  public AuthResponse refreshToken(String refreshToken) {
    String subject;
    try {
      subject = jwtService.validateRefreshToken(refreshToken);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired.");
    }
    
    java.util.UUID userId;
    try {
      userId = java.util.UUID.fromString(subject);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token subject.");
    }

    AuthUserEntity user = authUserRepository.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found."));
    
    return toResponse(user);
  }

  @Override
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
    String refreshToken = jwtService.generateRefreshToken(user.getId().toString());
    return new AuthResponse(
        token,
        refreshToken,
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

  // ── Password Reset ────────────────────────────────────────────────────────

  @Override
  @Transactional
  public void forgotPassword(ForgotPasswordRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    // Always respond with success to prevent user enumeration
    authUserRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
      // Invalidate any existing unused tokens for this user
      passwordResetTokenRepository.deleteUnusedByUserId(user.getId());

      // Generate cryptographically secure random token
      byte[] tokenBytes = new byte[TOKEN_BYTES];
      secureRandom.nextBytes(tokenBytes);
      String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
      String tokenHash = sha256Hex(rawToken);

      PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity();
      tokenEntity.setUser(user);
      tokenEntity.setTokenHash(tokenHash);
      tokenEntity.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
      passwordResetTokenRepository.save(tokenEntity);

      // In a real deployment, rawToken would be emailed here.
      // For now, log it at INFO level so it can be retrieved from logs during development.
      // TODO: integrate email service (e.g. SendGrid / SES) to deliver the token.
      org.slf4j.LoggerFactory.getLogger(AuthServiceImpl.class)
          .info("[DEV] Password reset token for {}: {}", normalizedEmail, rawToken);
    });
  }

  @Override
  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    String tokenHash = sha256Hex(request.token().trim());
    PasswordResetTokenEntity tokenEntity = passwordResetTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new ApiException(
            HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Reset token is invalid or has already been used."));

    if (tokenEntity.isUsed()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "RESET_TOKEN_USED", "This reset token has already been used.");
    }
    if (tokenEntity.isExpired()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "RESET_TOKEN_EXPIRED", "Reset token has expired. Please request a new one.");
    }

    AuthUserEntity user = tokenEntity.getUser();
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    authUserRepository.save(user);

    tokenEntity.setUsedAt(Instant.now());
    passwordResetTokenRepository.save(tokenEntity);
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
