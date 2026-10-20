package com.project.wastepilot.service.impl;

import com.project.wastepilot.config.WastePilotThresholds;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
@Slf4j
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private static final int ROLLING_WINDOW_DAYS = 30;
    private static final String STATISTICAL_MODE = "STATISTICAL_BASELINE";
    private static final String PROVISIONAL_MODE = "PROVISIONAL_THRESHOLD";

    private final WasteLogRepository wasteLogRepository;
    private final AnomalyRepository anomalyRepository;
    private final BigDecimal coldStartThresholdKg;
    private final WastePilotThresholds thresholds;

    public AnomalyDetectionServiceImpl(
            WasteLogRepository wasteLogRepository,
            AnomalyRepository anomalyRepository,
            @Value("${wastepilot.anomaly.cold-start-threshold-kg:10}") BigDecimal coldStartThresholdKg,
            WastePilotThresholds thresholds
    ) {
        this.wasteLogRepository = wasteLogRepository;
        this.anomalyRepository = anomalyRepository;
        this.coldStartThresholdKg = coldStartThresholdKg;
        this.thresholds = thresholds;
    }

    @Override
    @Transactional
    public void evaluateWasteLog(WasteLogEntity savedLog) {
        Instant thirtyDaysAgo = Instant.now().minus(ROLLING_WINDOW_DAYS, ChronoUnit.DAYS);
        // Using a time-based window rather than N-batches because with sparse seed data,
        // a time window provides a more stable baseline - each material/line may have very few batches,
        // but time-based gives us a guaranteed recency bound.
        // IMPORTANT: exclude the just-saved log itself so its own value doesn't suppress its own z-score.
        List<WasteLogEntity> recentLogs = wasteLogRepository.findByTimestampBetweenOrderByTimestampAsc(thirtyDaysAgo, Instant.now())
                .stream()
                .filter(log -> log.getMaterialName().equalsIgnoreCase(savedLog.getMaterialName()))
                .filter(log -> !log.getId().equals(savedLog.getId())) // exclude self from baseline
                .toList();

        // Need at least 2 historical points to compute a meaningful sample stddev
        if (recentLogs.size() < 2) {
            detectProvisionalAnomaly(savedLog);
            return;
        }

        int n = recentLogs.size();
        BigDecimal sum = recentLogs.stream().map(WasteLogEntity::getQuantityKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);

        BigDecimal sumOfSquaredDifferences = recentLogs.stream()
                .map(WasteLogEntity::getQuantityKg)
                .map(q -> q.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sample standard deviation: divide by (n-1) instead of n to get an unbiased estimator.
        // With population stddev at small n, the max achievable z-score is bounded by sqrt(n-1)/sqrt(1)
        // which at n=9 is only ~2.83 — barely above our 2.5 threshold and unreachable for n<8.
        BigDecimal variance = sumOfSquaredDifferences.divide(BigDecimal.valueOf(n - 1), 6, RoundingMode.HALF_UP);

        // Standard deviation (approximate square root for BigDecimal)
        double stddevDouble = Math.sqrt(variance.doubleValue());
        BigDecimal stddev = BigDecimal.valueOf(stddevDouble);

        if (stddev.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Zero standard deviation for material {}", savedLog.getMaterialName());
            detectProvisionalAnomaly(savedLog);
            return;
        }

        BigDecimal zScore = savedLog.getQuantityKg().subtract(mean).divide(stddev, 6, RoundingMode.HALF_UP);

        if (zScore.compareTo(BigDecimal.valueOf(thresholds.getAnomalyZScoreThreshold())) > 0) {
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
            anomaly.setDetectionMode(STATISTICAL_MODE);
            anomaly.setProvisionalThresholdKg(null);
            anomaly.setWasteKg(savedLog.getQuantityKg());
            anomaly.setDate(dateStr);
            // Rule-based recommendation, not AI-generated
            anomaly.setNote(String.format("Waste quantity %s kg is %s standard deviations above the %d-day mean of %s kg for this material (sample stddev, n=%d, excluding self). (Rule-based detection)",
                    savedLog.getQuantityKg().setScale(3, RoundingMode.HALF_UP).toPlainString(),
                    zScore.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    ROLLING_WINDOW_DAYS,
                    mean.setScale(3, RoundingMode.HALF_UP).toPlainString(),
                    n));
            anomaly.setStatus(InsightStatus.NEW);
            anomaly.setTimestamp(Instant.now());

            anomalyRepository.save(anomaly);
        }
    }

    private void detectProvisionalAnomaly(WasteLogEntity savedLog) {
        if (savedLog.getQuantityKg().compareTo(coldStartThresholdKg) < 0) {
            log.debug("No cold-start anomaly for material {} below provisional threshold {} kg",
                    savedLog.getMaterialName(), coldStartThresholdKg);
            return;
        }

        String dateStr = LocalDate.now(ZoneId.of("UTC")).toString();
        if (anomalyRepository.existsByBatchIdAndMaterialNameAndDateAndStatus(
                savedLog.getBatch().getId(),
                savedLog.getMaterialName(),
                dateStr,
                InsightStatus.NEW)) {
            return;
        }

        AnomalyEntity anomaly = new AnomalyEntity();
        anomaly.setBatchId(savedLog.getBatch().getId());
        anomaly.setMaterialName(savedLog.getMaterialName());
        anomaly.setProcess(savedLog.getMaterialName() + " / " + savedLog.getBatch().getTemplateName());
        anomaly.setZScore(null);
        anomaly.setDetectionMode(PROVISIONAL_MODE);
        anomaly.setProvisionalThresholdKg(coldStartThresholdKg);
        anomaly.setWasteKg(savedLog.getQuantityKg());
        anomaly.setDate(dateStr);
        anomaly.setNote(String.format(
                "Cold-start provisional threshold: waste quantity %s kg met or exceeded the configured %s kg threshold. Statistical baseline is unavailable.",
                savedLog.getQuantityKg().setScale(3, RoundingMode.HALF_UP).toPlainString(),
                coldStartThresholdKg.stripTrailingZeros().toPlainString()));
        anomaly.setStatus(InsightStatus.NEW);
        anomaly.setTimestamp(Instant.now());
        anomalyRepository.save(anomaly);
    }
}
