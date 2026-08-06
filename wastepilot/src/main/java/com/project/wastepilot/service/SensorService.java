package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.operations.InventoryLogResponse;
import com.project.wastepilot.domain.dto.sensor.SensorIngestRequest;

public interface SensorService {
    InventoryLogResponse ingest(SensorIngestRequest request);
}
