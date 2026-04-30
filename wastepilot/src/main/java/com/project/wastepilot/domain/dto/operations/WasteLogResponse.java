package com.project.wastepilot.domain.dto.operations;

import com.project.wastepilot.domain.enums.*;

import java.math.BigDecimal;
import java.time.Instant;

public record WasteLogResponse(
        String id,
        String batchId,
        String materialName,
        BigDecimal quantityKg,
        String destination,
        String reason,
        String recoveryStatus,
        String recoveryInventoryLogId,
        Instant recoveredAt,
        String aiSuggestedAction,
        Boolean isRepurposed,
        Instant timestamp
) {}
