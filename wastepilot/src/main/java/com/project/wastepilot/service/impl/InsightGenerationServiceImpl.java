package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InsightEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.InsightStatus;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InsightRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.InsightGenerationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightGenerationServiceImpl implements InsightGenerationService {

    private final BatchRepository batchRepository;
    private final WasteLogRepository wasteLogRepository;
    private final TemplateRepository templateRepository;
    private final InsightRepository insightRepository;

    @Override
    @Transactional
    public void evaluateAfterBatchClose(BatchEntity batch) {
        // Evaluate rule 1: Landfill share trend
        evaluateLandfillShareTrend(batch);
        
        // Evaluate rule 2: Disproportionate waste
        evaluateDisproportionateWaste(batch);
    }

    private void evaluateLandfillShareTrend(BatchEntity currentBatch) {
        // Look at the last 3 completed batches for the same template
        List<BatchEntity> recentBatches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.completed).stream()
                .filter(b -> b.getTemplateName().equalsIgnoreCase(currentBatch.getTemplateName()))
                .sorted(Comparator.comparing(BatchEntity::getStartedAt).reversed())
                .limit(3)
                .toList();

        if (recentBatches.size() < 3) {
            return; // Not enough history
        }

        // Calculate landfill share for the 3 batches (0 is newest, 2 is oldest)
        BigDecimal share0 = calculateLandfillShare(recentBatches.get(0));
        BigDecimal share1 = calculateLandfillShare(recentBatches.get(1));
        BigDecimal share2 = calculateLandfillShare(recentBatches.get(2));

        // If increased for 2 consecutive periods: share0 > share1 AND share1 > share2
        if (share0.compareTo(share1) > 0 && share1.compareTo(share2) > 0) {
            String ruleId = "LANDFILL_TREND_" + currentBatch.getTemplateName().toUpperCase().replaceAll("\\s+", "_");
            
            if (insightRepository.existsByRuleIdAndStatus(ruleId, InsightStatus.NEW)) {
                return; // Already an active insight for this trend
            }

            InsightEntity insight = new InsightEntity();
            insight.setRuleId(ruleId);
            insight.setTitle("Rising Landfill Share: " + currentBatch.getTemplateName());
            // Rule-based recommendation, not AI-generated
            insight.setContent(String.format("Landfill share for %s has increased over the last 3 consecutive batches (%.1f%% -> %.1f%% -> %.1f%%). Recommend investigating disposal practices or material quality. (Rule-based recommendation)", 
                currentBatch.getTemplateName(),
                share2.multiply(new BigDecimal("100")).floatValue(),
                share1.multiply(new BigDecimal("100")).floatValue(),
                share0.multiply(new BigDecimal("100")).floatValue()));
            insight.setImpactCategory("Sustainability");
            insight.setStatus(InsightStatus.NEW);
            insight.setTimestamp(Instant.now());
            insightRepository.save(insight);
        }
    }

    private BigDecimal calculateLandfillShare(BatchEntity batch) {
        List<WasteLogEntity> wasteLogs = wasteLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId());
        BigDecimal totalWaste = wasteLogs.isEmpty()
                ? batch.getWasteKg().max(BigDecimal.ZERO)
                : wasteLogs.stream().map(WasteLogEntity::getQuantityKg).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWaste.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal disposeKg = wasteLogs.stream()
                .filter(log -> log.getDestination() == WasteDestination.dispose)
                .map(WasteLogEntity::getQuantityKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return disposeKg.divide(totalWaste, 6, RoundingMode.HALF_UP);
    }

    private void evaluateDisproportionateWaste(BatchEntity batch) {
        TemplateEntity template = templateRepository.findByNameIgnoreCase(batch.getTemplateName()).orElse(null);
        if (template == null || template.getLines().isEmpty()) {
            return;
        }

        BigDecimal totalInputKg = template.getLines().stream()
                .map(line -> line.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalInputKg.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        List<WasteLogEntity> wasteLogs = wasteLogRepository.findByBatchIdOrderByTimestampDesc(batch.getId());
        BigDecimal totalWasteKg = wasteLogs.stream().map(WasteLogEntity::getQuantityKg).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWasteKg.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // Group waste by material
        for (var line : template.getLines()) {
            String materialName = line.getMaterial().getName();
            
            BigDecimal materialWasteKg = wasteLogs.stream()
                    .filter(log -> log.getMaterialName().equalsIgnoreCase(materialName))
                    .map(WasteLogEntity::getQuantityKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (materialWasteKg.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal inputShare = line.getQuantity().divide(totalInputKg, 6, RoundingMode.HALF_UP);
                BigDecimal wasteShare = materialWasteKg.divide(totalWasteKg, 6, RoundingMode.HALF_UP);

                // If material contributes >40% of total waste while representing <20% of input
                if (wasteShare.compareTo(new BigDecimal("0.4")) > 0 && inputShare.compareTo(new BigDecimal("0.2")) < 0) {
                    String rawRuleId = "DISP_WASTE_" + batch.getId() + "_" + materialName.toUpperCase().replaceAll("\\s+", "_");
                    String ruleId = rawRuleId.length() > 64 ? rawRuleId.substring(0, 64) : rawRuleId;
                    
                    if (insightRepository.existsByRuleIdAndStatus(ruleId, InsightStatus.NEW)) {
                        continue;
                    }

                    InsightEntity insight = new InsightEntity();
                    insight.setRuleId(ruleId);
                    insight.setTitle("Disproportionate Waste: " + materialName);
                    // Rule-based recommendation, not AI-generated
                    insight.setContent(String.format("Material %s contributes %.1f%% of total waste for batch %s despite being only %.1f%% of planned input. Recommend reviewing material handling or supplier quality. (Rule-based recommendation)",
                            materialName,
                            wasteShare.multiply(new BigDecimal("100")).floatValue(),
                            batch.getId(),
                            inputShare.multiply(new BigDecimal("100")).floatValue()));
                    insight.setImpactCategory("Efficiency");
                    insight.setStatus(InsightStatus.NEW);
                    insight.setTimestamp(Instant.now());
                    insightRepository.save(insight);
                }
            }
        }
    }
}
