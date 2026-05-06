package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.settings.UpdateUserSettingsRequest;
import com.project.wastepilot.domain.dto.settings.UserSettingsResponse;
import com.project.wastepilot.domain.entity.AuthUserEntity;

public interface SettingsService {
    UserSettingsResponse getCurrentSettings();
    UserSettingsResponse updateCurrentSettings(UpdateUserSettingsRequest request);
    void createDefaultSettingsForUser(AuthUserEntity user);
}
