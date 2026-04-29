package com.project.wastepilot.domain.dto.operations;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryLogResponse(
        String id,
        String batchId,
        String materialName,
        String type,
        BigDecimal quantity,
        String unit,
        String source,
        Instant timestamp
) {}
