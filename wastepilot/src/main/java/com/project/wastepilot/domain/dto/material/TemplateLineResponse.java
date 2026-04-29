package com.project.wastepilot.domain.dto.material;

import java.math.BigDecimal;

public record TemplateLineResponse(
  String materialId,
  String materialName,
  BigDecimal quantity,
  String unit
) {}
