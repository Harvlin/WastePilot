package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.operations.InventoryLogResponse;
import com.project.wastepilot.domain.dto.sensor.SensorIngestRequest;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.enums.InventoryType;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.service.OperationsService;
import com.project.wastepilot.service.SensorService;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorServiceImpl implements SensorService {

    private final OperationsService operationsService;
    private final InventoryLogRepository inventoryLogRepository;

    @Override
    @Transactional
    public InventoryLogResponse ingest(SensorIngestRequest request) {
        BatchEntity batch = operationsService.resolveRunningBatch(request.batchId());

        InventoryLogEntity logEntity = new InventoryLogEntity();
        logEntity.setBatch(batch);
        logEntity.setMaterialName(request.materialName());
        logEntity.setType(InventoryType.IN);
        logEntity.setQuantity(request.quantity().setScale(3, RoundingMode.HALF_UP));
        logEntity.setUnit(request.unit());
        logEntity.setSource("sensor");
        logEntity.setTimestamp(request.timestamp() != null ? request.timestamp() : Instant.now());

        InventoryLogEntity saved = inventoryLogRepository.save(logEntity);
        log.info("Ingested sensor data for batch {}: {} {}", batch.getId(), request.quantity(), request.unit());
        
        return toInventoryLogResponse(saved);
    }
    
    private InventoryLogResponse toInventoryLogResponse(InventoryLogEntity entity) {
        return new InventoryLogResponse(
                entity.getId().toString(),
                entity.getBatch() != null ? entity.getBatch().getId().toString() : null,
                entity.getMaterialName(),
                entity.getType().name(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getSource(),
                entity.getTimestamp()
        );
    }
}
