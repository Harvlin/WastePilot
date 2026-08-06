package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IntegrityMapper {
    @Mapping(target = "batchId", source = "entityId")
    @Mapping(target = "source", constant = "system")
    @Mapping(target = "details", source = "detail")
    ActivityLogEntryResponse toResponse(ActivityLogEntity entity);

    @Mapping(target = "batchId", source = "entityId")
    @Mapping(target = "editedBy", source = "actor")
    @Mapping(target = "editedAt", source = "timestamp")
    AuditTrailEntryResponse toResponse(AuditTrailEntity entity);
}
