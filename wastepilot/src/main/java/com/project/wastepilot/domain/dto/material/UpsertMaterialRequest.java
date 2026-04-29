package com.project.wastepilot.domain.dto.material;

import com.project.wastepilot.domain.enums.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record UpsertMaterialRequest(
  String id,
  @NotBlank String name,
  @NotBlank String category,
  @NotBlank String unit,
  @NotBlank String circularGrade,
  @DecimalMin("0.0") BigDecimal stock,
  @NotBlank String supplier
) {}
