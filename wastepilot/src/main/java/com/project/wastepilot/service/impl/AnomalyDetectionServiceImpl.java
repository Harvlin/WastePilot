package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.entity.AnomalyEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.InsightStatus;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.AnomalyDetectionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private static final BigDecimal ANOMALY_Z_SCORE_THRESHOLD = new BigDecimal("2.5");
    private static final int ROLLING_WINDOW_DAYS = 30;

    private final WasteLogRepository wasteLogRepository;
    private final AnomalyRepository anomalyRepository;

    @Override
    @Transactional
    public void evaluateWasteLog(WasteLogEntity savedLog) {
        Instant thirtyDaysAgo = Instant.now().minus(ROLLING_WINDOW_DAYS, ChronoUnit.DAYS);
        // Using a time-based window rather than N-batches because with sparse seed data,
        // a time window provides a more stable baseline - each material/line may have very few batches,
        // but time-based gives us a guaranteed recency bound.
        List<WasteLogEntity> recentLogs = wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(thirtyDaysAgo, Instant.now())
                .stream()
                .filter(log -> log.getMaterialName().equalsIgnoreCase(savedLog.getMaterialName()))
                .toList();

        if (recentLogs.size() < 2) {
            log.debug("Not enough data points to compute anomaly for material {}", savedLog.getMaterialName());
            return;
        }

        BigDecimal sum = recentLogs.stream().map(WasteLogEntity::getQuantityKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(recentLogs.size()), 6, RoundingMode.HALF_UP);

        BigDecimal sumOfSquaredDifferences = recentLogs.stream()
                .map(WasteLogEntity::getQuantityKg)
                .map(q -> q.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance = sumOfSquaredDifferences.divide(BigDecimal.valueOf(recentLogs.size()), 6, RoundingMode.HALF_UP);

        // Standard deviation (approximate square root for BigDecimal)
        double stddevDouble = Math.sqrt(variance.doubleValue());
        BigDecimal stddev = BigDecimal.valueOf(stddevDouble);

        if (stddev.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Zero standard deviation for material {}", savedLog.getMaterialName());
            return; // Cannot compute z-score if all values are identical
        }

        BigDecimal zScore = savedLog.getQuantityKg().subtract(mean).divide(stddev, 6, RoundingMode.HALF_UP);

        if (zScore.compareTo(ANOMALY_Z_SCORE_THRESHOLD) > 0) {
            String dateStr = LocalDate.now(ZoneId.of("UTC")).toString();
            
            boolean exists = anomalyRepository.existsByBatchIdAndMaterialNameAndDateAndStatus(
                    savedLog.getBatch().getId(),
                    savedLog.getMaterialName(),
                    dateStr,
                    InsightStatus.NEW
            );

            if (exists) {
                log.debug("Anomaly already recorded for batch {} material {} today", savedLog.getBatch().getId(), savedLog.getMaterialName());
                return;
            }

            AnomalyEntity anomaly = new AnomalyEntity();
            anomaly.setBatchId(savedLog.getBatch().getId());
            anomaly.setMaterialName(savedLog.getMaterialName());
            anomaly.setProcess(savedLog.getMaterialName() + " / " + savedLog.getBatch().getTemplateName());
            anomaly.setZScore(zScore.setScale(2, RoundingMode.HALF_UP));
            anomaly.setWasteKg(savedLog.getQuantityKg());
            anomaly.setDate(dateStr);
            // Rule-based recommendation, not AI-generated
            anomaly.setNote(String.format("Waste quantity %s kg is %s standard deviations above the %d-day mean of %s kg for this material. (Rule-based detection)",
                    savedLog.getQuantityKg().setScale(3, RoundingMode.HALF_UP).toPlainString(),
                    zScore.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    ROLLING_WINDOW_DAYS,
                    mean.setScale(3, RoundingMode.HALF_UP).toPlainString()));
            anomaly.setStatus(InsightStatus.NEW);
            anomaly.setTimestamp(Instant.now());

            anomalyRepository.save(anomaly);
        }
    }
}
