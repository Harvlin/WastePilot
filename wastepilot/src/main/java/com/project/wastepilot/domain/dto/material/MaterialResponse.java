package com.project.wastepilot.domain.dto.material;

import com.project.wastepilot.domain.enums.*;

import java.math.BigDecimal;

public record MaterialResponse(
  String id,
  String name,
  String category,
  String unit,
  String circularGrade,
  BigDecimal stock,
  String supplier
) {}
