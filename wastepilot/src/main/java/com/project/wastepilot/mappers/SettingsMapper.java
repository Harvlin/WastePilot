package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.settings.UserSettingsResponse;
import com.project.wastepilot.domain.dto.settings.UpdateUserSettingsRequest;
import com.project.wastepilot.domain.entity.UserSettingsEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface SettingsMapper {
    @Mapping(target = "companyName", source = "company")
    @Mapping(target = "notifyInsights", source = "notifyRecommendations")
    UserSettingsResponse toResponse(UserSettingsEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "company", source = "companyName")
    @Mapping(target = "notifyRecommendations", source = "notifyInsights")
    @Mapping(target = "notifyOverdueBatches", ignore = true)
    @Mapping(target = "id", ignore = true)
    void update(@MappingTarget UserSettingsEntity target, UpdateUserSettingsRequest source);
}
