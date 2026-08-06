package com.project.wastepilot.service;

import com.project.wastepilot.domain.entity.BatchEntity;

public interface InsightGenerationService {
    void evaluateAfterBatchClose(BatchEntity batch);
}
