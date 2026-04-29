package com.project.wastepilot.domain.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record TemplateLineRequest(
  @NotBlank String materialId,
  @NotBlank String materialName,
  @DecimalMin("0.001") BigDecimal quantity,
  @NotBlank String unit
) {}
