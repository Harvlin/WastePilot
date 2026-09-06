package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IntegrityMapper {
    @Mapping(target = "batchId", ignore = true)
    @Mapping(target = "source", constant = "manual")
    @Mapping(target = "details", source = "detail")
    ActivityLogEntryResponse toResponse(ActivityLogEntity entity);

    @Mapping(target = "batchId", ignore = true)
    @Mapping(target = "editedBy", source = "actor")
    @Mapping(target = "editedAt", source = "timestamp")
    AuditTrailEntryResponse toResponse(AuditTrailEntity entity);
}
