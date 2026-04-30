package com.project.wastepilot.domain.dto.integrity;

import java.time.Instant;

public record AuditTrailEntryResponse(
  String id,
  String batchId,
  String field,
  String oldValue,
  String newValue,
  String editedBy,
  Instant editedAt,
  String reason
) {}
