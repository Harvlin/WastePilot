package com.project.wastepilot.domain.dto.analytics;

import java.math.BigDecimal;

public record ReportTrendPoint(
  String label,
  BigDecimal circularScore,
  BigDecimal recoveredKg,
  BigDecimal landfillKg
) {}
