package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.settings.UpdateUserSettingsRequest;
import com.project.wastepilot.domain.dto.settings.UserSettingsResponse;
import com.project.wastepilot.domain.entity.AuthUserEntity;
import com.project.wastepilot.domain.entity.UserSettingsEntity;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.mappers.SettingsMapper;
import com.project.wastepilot.repository.AuthUserRepository;
import com.project.wastepilot.repository.UserSettingsRepository;
import com.project.wastepilot.security.SecurityUtils;
import com.project.wastepilot.service.SettingsService;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

  private static final int DEFAULT_DAILY_TOKEN_BUDGET = 12000;
  private static final String DEFAULT_TIMEZONE = "UTC";

  private final UserSettingsRepository userSettingsRepository;
  private final AuthUserRepository authUserRepository;
  private final SettingsMapper settingsMapper;

  @Override
  @Transactional
  public UserSettingsResponse getCurrentSettings() {
    String userId = resolveCurrentUserId();
    UserSettingsEntity settings = userSettingsRepository.findByUserId(userId)
        .orElseGet(() -> userSettingsRepository.save(createDefaultSettings(userId)));
    return settingsMapper.toResponse(settings);
  }

  @Override
  @Transactional
  public UserSettingsResponse updateCurrentSettings(UpdateUserSettingsRequest request) {
    String userId = resolveCurrentUserId();
    UserSettingsEntity settings = userSettingsRepository.findByUserId(userId)
        .orElseGet(() -> userSettingsRepository.save(createDefaultSettings(userId)));

    String previousRole = settings.getRole();
    settingsMapper.update(settings, request);
    settings.setCompany(settings.getCompany().trim());
    settings.setEmail(settings.getEmail().trim().toLowerCase());
    String updatedRole = settings.getRole();
    if (updatedRole == null || updatedRole.isBlank()) {
      settings.setRole(previousRole == null ? "Operator" : previousRole.trim());
    } else {
      settings.setRole(updatedRole.trim());
    }
    settings.setTimezone(validateTimezone(settings.getTimezone()));
    UserSettingsEntity saved = userSettingsRepository.save(settings);
    return settingsMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public void createDefaultSettingsForUser(AuthUserEntity user) {
    if (userSettingsRepository.findByUserId(user.getId().toString()).isPresent()) {
      return;
    }

    UserSettingsEntity settings = new UserSettingsEntity();
    settings.setUserId(user.getId().toString());
    settings.setCompany("WastePilot Workspace");
    settings.setEmail(user.getEmail());
    settings.setRole("Operator");
    settings.setTimezone(DEFAULT_TIMEZONE);
    settings.setDailyTokenBudget(DEFAULT_DAILY_TOKEN_BUDGET);
    settings.setNotifyAnomalies(true);
    settings.setNotifyRecommendations(true);
    settings.setNotifyOverdueBatches(true);
    userSettingsRepository.save(settings);
  }

  private UserSettingsEntity createDefaultSettings(String userId) {
    UUID uuid = parseUserUuid(userId);
    AuthUserEntity user = authUserRepository.findById(uuid)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

    UserSettingsEntity settings = new UserSettingsEntity();
    settings.setUserId(userId);
    settings.setCompany("WastePilot Workspace");
    settings.setEmail(user.getEmail());
    settings.setRole("Operator");
    settings.setTimezone(DEFAULT_TIMEZONE);
    settings.setDailyTokenBudget(DEFAULT_DAILY_TOKEN_BUDGET);
    settings.setNotifyAnomalies(true);
    settings.setNotifyRecommendations(true);
    settings.setNotifyOverdueBatches(true);
    return settings;
  }

  private String resolveCurrentUserId() {
    String userId = SecurityUtils.getCurrentUserId();
    parseUserUuid(userId);
    return userId;
  }

  private UUID parseUserUuid(String rawUserId) {
    try {
      return UUID.fromString(rawUserId);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid authentication subject.");
    }
  }

  private String validateTimezone(String timezone) {
    String normalized = timezone == null ? "" : timezone.trim();
    try {
      ZoneId.of(normalized);
      return normalized;
    } catch (DateTimeException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Timezone is invalid.");
    }
  }
}
