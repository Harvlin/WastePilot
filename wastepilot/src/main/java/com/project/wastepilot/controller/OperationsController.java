package com.project.wastepilot.controller;

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
import com.project.wastepilot.service.OperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationsController {
  private final OperationsService operationsService;

  @GetMapping
  public OperationsPayloadResponse getPayload() {
    return operationsService.getOperationsPayload();
  }

  @PostMapping("/batches")
  public BatchResponse createBatch(@Valid @RequestBody CreateBatchRequest request) {
    return operationsService.createBatch(request);
  }

  @PostMapping("/inventory-logs")
  public InventoryLogResponse createInventoryLog(@Valid @RequestBody CreateInventoryLogRequest request) {
    return operationsService.createInventoryLog(request);
  }

  @PostMapping("/waste-logs")
  public WasteLogResponse createWasteLog(@Valid @RequestBody CreateWasteLogRequest request) {
    return operationsService.createWasteLog(request);
  }

  @PostMapping("/waste-logs/recover")
  public WasteRecoveryResponse recoverWaste(@Valid @RequestBody RecoverWasteRequest request) {
    return operationsService.recoverWasteToInventory(request);
  }

  @GetMapping("/batch-close/summary/{batchId}")
  public BatchCloseSummaryResponse getBatchCloseSummary(@PathVariable String batchId) {
    return operationsService.getBatchCloseSummary(batchId);
  }

  @PostMapping("/batch-close")
  public BatchCloseSummaryResponse closeBatch(@Valid @RequestBody CloseBatchRequest request) {
    return operationsService.closeBatch(request);
  }
}

