package com.project.wastepilot.domain.dto.ocr;

import java.math.BigDecimal;

public record OcrMaterialLineResponse(
  String id,
  String materialName,
  BigDecimal quantity,
  String unit,
  BigDecimal price
) {}
