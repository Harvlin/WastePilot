package com.project.wastepilot.domain.dto.integrity;

import java.time.Instant;

public record AuditTrailEntryResponse(
  String id,
  String entity,
  String entityId,
  String field,
  String oldValue,
  String newValue,
  String actor,
  Instant timestamp
) {}
