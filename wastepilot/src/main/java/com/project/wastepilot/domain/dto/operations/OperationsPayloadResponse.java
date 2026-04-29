package com.project.wastepilot.domain.dto.operations;

import java.util.List;

public record OperationsPayloadResponse(
        List<BatchResponse> batches,
        List<InventoryLogResponse> inventoryLogs,
        List<WasteLogResponse> wasteLogs
) {}
