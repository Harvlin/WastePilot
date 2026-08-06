package com.project.wastepilot.domain.dto.integrity;

import java.math.BigDecimal;

/**
 * Represents a detected discrepancy between manual/OCR and sensor-sourced inventory inputs
 * for the same material within the same batch.
 *
 * This record is factual and neutral — it reports a data discrepancy for review,
 * not an accusation of wrongdoing.
 */
public record CrossValidationDiscrepancy(
    String batchId,
    String materialName,
    /** Sum of all manual + OCR inventory IN entries (kg) for this batch/material pair. */
    BigDecimal manualTotalKg,
    /** Sum of all sensor inventory IN entries (kg) for this batch/material pair. */
    BigDecimal sensorTotalKg,
    /** Relative discrepancy: |manual - sensor| / max(manual, sensor) * 100 */
    BigDecimal relativeDiscrepancyPercent,
    /** Descriptive note explaining the discrepancy in neutral terms. */
    String note
) {}
