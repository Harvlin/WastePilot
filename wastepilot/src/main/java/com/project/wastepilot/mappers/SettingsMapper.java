package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.settings.UserSettingsResponse;
import com.project.wastepilot.domain.entity.UserSettingsEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SettingsMapper {
    UserSettingsResponse toResponse(UserSettingsEntity entity);
}
