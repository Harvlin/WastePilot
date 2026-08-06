package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.integrity.ActivityLogEntryResponse;
import com.project.wastepilot.domain.dto.integrity.AuditTrailEntryResponse;
import com.project.wastepilot.domain.entity.ActivityLogEntity;
import com.project.wastepilot.domain.entity.AuditTrailEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for integrity DTOs.
 *
 * NOTE: IntegrityServiceImpl performs its own manual mapping to support derived fields
 * (e.g. batchId resolved via entity-type lookup, source derived from detail content).
 * This mapper is kept as a structural contract reference and is not invoked at runtime.
 * If the manual mapping is ever removed in favour of this mapper, the @Mapping
 * annotations here must be updated to match the full resolution logic.
 */
@Mapper(componentModel = "spring")
public interface IntegrityMapper {
    // batchId cannot be mapped 1:1 from entityId because entityId may be an inventory or waste
    // log ID — the actual batchId requires a repository lookup (see IntegrityServiceImpl).
    // The manual toActivityResponse() handles this correctly.
    @Mapping(target = "batchId", ignore = true)
    @Mapping(target = "source", constant = "manual")
    @Mapping(target = "details", source = "detail")
    ActivityLogEntryResponse toResponse(ActivityLogEntity entity);

    // batchId and editedBy are derived fields; ignore here to avoid incorrect mapping.
    @Mapping(target = "batchId", ignore = true)
    @Mapping(target = "editedBy", source = "actor")
    @Mapping(target = "editedAt", source = "timestamp")
    AuditTrailEntryResponse toResponse(AuditTrailEntity entity);
}
