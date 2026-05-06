package com.project.wastepilot.domain.dto.insight;

import java.math.BigDecimal;

public record AnomalyResponse(
  String id,
  String process,
  BigDecimal zScore,
  BigDecimal wasteKg,
  String date,
  String note,
  String status
) {}
