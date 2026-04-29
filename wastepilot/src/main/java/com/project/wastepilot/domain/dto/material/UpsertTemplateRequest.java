package com.project.wastepilot.domain.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;

public record UpsertTemplateRequest(
  String id,
  @NotBlank String name,
  @NotBlank String sku,
  @DecimalMin("0.0") BigDecimal expectedWasteKg,
  @NotEmpty List<TemplateLineRequest> lines
) {}
