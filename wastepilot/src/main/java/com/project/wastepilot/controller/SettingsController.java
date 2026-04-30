package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.settings.UpdateUserSettingsRequest;
import com.project.wastepilot.domain.dto.settings.UserSettingsResponse;
import com.project.wastepilot.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

  private final SettingsService settingsService;

  @GetMapping
  public UserSettingsResponse getSettings() {
    return settingsService.getCurrentSettings();
  }

  @PutMapping
  public UserSettingsResponse updateSettings(@Valid @RequestBody UpdateUserSettingsRequest request) {
    return settingsService.updateCurrentSettings(request);
  }
}
