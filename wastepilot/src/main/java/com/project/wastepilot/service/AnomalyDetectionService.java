package com.project.wastepilot.service;

import com.project.wastepilot.domain.entity.WasteLogEntity;

public interface AnomalyDetectionService {
    void evaluateWasteLog(WasteLogEntity savedLog);
}
