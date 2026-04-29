package com.project.wastepilot.domain.dto.insight;

import java.math.BigDecimal;
import java.time.Instant;

public record AnomalyResponse(
  String id,
  String process,
  BigDecimal zScore,
  BigDecimal wasteKg,
  String date,
  String note,
  String status,
  Instant timestamp
) {}
