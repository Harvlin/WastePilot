package com.project.wastepilot.domain.dto.integrity;

import java.time.Instant;

public record ActivityLogEntryResponse(
  String id,
  String batchId,
  String actor,
  String action,
  String entity,
  String entityId,
  String source,
  String details,
  Instant timestamp
) {}
