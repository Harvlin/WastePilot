package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IntegrityMapper {
    ActivityLogEntryResponse toResponse(ActivityLogEntity entity);
    AuditTrailEntryResponse toResponse(AuditTrailEntity entity);
}
