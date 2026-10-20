package com.project.wastepilot.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.project.wastepilot.config.WastePilotThresholds;
import com.project.wastepilot.domain.dto.operations.CreateBatchRequest;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.TemplateLineEntity;
import com.project.wastepilot.repository.ActivityLogRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InventoryLogRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.AnomalyDetectionService;
import com.project.wastepilot.service.InsightGenerationService;
import com.project.wastepilot.service.impl.AuditChainService;
import com.project.wastepilot.service.impl.BatchMetricsService;
import com.project.wastepilot.service.impl.FloorViewService;
import com.project.wastepilot.service.impl.OperationsServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchPlannedInputSnapshotTest {
  @Mock private BatchRepository batchRepository;
  @Mock private InventoryLogRepository inventoryLogRepository;
  @Mock private WasteLogRepository wasteLogRepository;
  @Mock private TemplateRepository templateRepository;
  @Mock private ActivityLogRepository activityLogRepository;
  @Mock private AuditChainService auditChainService;
  @Mock private BatchMetricsService batchMetricsService;
  @Mock private FloorViewService floorViewService;
  @Mock private AnomalyDetectionService anomalyDetectionService;
  @Mock private InsightGenerationService insightGenerationService;

  @Test
  void createBatchCapturesCurrentTemplatePlannedInput() {
    TemplateLineEntity firstLine = new TemplateLineEntity();
    firstLine.setQuantity(new BigDecimal("20.000"));
    TemplateLineEntity secondLine = new TemplateLineEntity();
    secondLine.setQuantity(new BigDecimal("35.000"));
    TemplateEntity template = new TemplateEntity();
    template.setLines(List.of(firstLine, secondLine));
    when(templateRepository.findByNameIgnoreCase("Template A")).thenReturn(Optional.of(template));
    when(batchRepository.save(any(BatchEntity.class))).thenAnswer(invocation -> {
      BatchEntity batch = invocation.getArgument(0);
      batch.setId(UUID.randomUUID());
      return batch;
    });

    OperationsServiceImpl service = new OperationsServiceImpl(
        batchRepository,
        inventoryLogRepository,
        wasteLogRepository,
        templateRepository,
        activityLogRepository,
        auditChainService,
        batchMetricsService,
        floorViewService,
        anomalyDetectionService,
        insightGenerationService,
        new WastePilotThresholds()
    );

    service.createBatch(new CreateBatchRequest("Template A", BigDecimal.TEN, BigDecimal.ZERO));

    org.mockito.ArgumentCaptor<BatchEntity> batchCaptor =
        org.mockito.ArgumentCaptor.forClass(BatchEntity.class);
    org.mockito.Mockito.verify(batchRepository).save(batchCaptor.capture());
    assertThat(batchCaptor.getValue().getPlannedInputKg()).isEqualByComparingTo("55.000");
  }
}
