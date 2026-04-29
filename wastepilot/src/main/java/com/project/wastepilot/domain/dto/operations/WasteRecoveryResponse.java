package com.project.wastepilot.domain.dto.operations;

public record WasteRecoveryResponse(
        WasteLogResponse wasteLog,
        InventoryLogResponse inventoryLog
) {}