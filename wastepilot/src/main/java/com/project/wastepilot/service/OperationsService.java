package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.operations.BatchCloseSummaryResponse;
import com.project.wastepilot.domain.dto.operations.BatchResponse;
import com.project.wastepilot.domain.dto.operations.CloseBatchRequest;
import com.project.wastepilot.domain.dto.operations.CreateBatchRequest;
import com.project.wastepilot.domain.dto.operations.CreateInventoryLogRequest;
import com.project.wastepilot.domain.dto.operations.CreateWasteLogRequest;
import com.project.wastepilot.domain.dto.operations.InventoryLogResponse;
import com.project.wastepilot.domain.dto.operations.OperationsPayloadResponse;
import com.project.wastepilot.domain.dto.operations.RecoverWasteRequest;
import com.project.wastepilot.domain.dto.operations.WasteLogResponse;
import com.project.wastepilot.domain.dto.operations.WasteRecoveryResponse;

public interface OperationsService {
    OperationsPayloadResponse getOperationsPayload();
    BatchResponse createBatch(CreateBatchRequest request);
    InventoryLogResponse createInventoryLog(CreateInventoryLogRequest request);
    WasteLogResponse createWasteLog(CreateWasteLogRequest request);
    WasteRecoveryResponse recoverWasteToInventory(RecoverWasteRequest request);
    BatchCloseSummaryResponse getBatchCloseSummary(String batchId);
    BatchCloseSummaryResponse closeBatch(CloseBatchRequest request);

    // Package-scoped helpers consumed by IntegrityService
    int getOverdueBatchClosuresCount();
    int getOpenRedFlagsCount();
    int getAverageConfidenceScoreForCompletedBatches();
}
