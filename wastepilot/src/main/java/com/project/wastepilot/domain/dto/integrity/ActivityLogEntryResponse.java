package com.project.wastepilot.domain.dto.integrity;

import java.time.Instant;

public record ActivityLogEntryResponse(
  String id,
  String entity,
  String entityId,
  String action,
  String actor,
  String detail,
  Instant timestamp
) {}
