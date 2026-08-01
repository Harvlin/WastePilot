package com.project.wastepilot.domain.dto.ai;

import java.util.UUID;

public record OcrMaterialLine(
    String id,
    String materialName,
    double quantity,
    String unit,
    double price
) {}
