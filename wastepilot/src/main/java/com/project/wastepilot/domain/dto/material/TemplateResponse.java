package com.project.wastepilot.domain.dto.material;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TemplateResponse(
  String id,
  String name,
  String sku,
  BigDecimal expectedWasteKg,
  Instant updatedAt,
  List<TemplateLineResponse> lines
) {}
