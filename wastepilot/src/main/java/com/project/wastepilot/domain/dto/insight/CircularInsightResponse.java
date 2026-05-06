package com.project.wastepilot.domain.dto.insight;

import java.time.Instant;

public record CircularInsightResponse(
  String id,
  String title,
  String content,
  String impactCategory,
  String status,
  Instant createdAt
) {}
