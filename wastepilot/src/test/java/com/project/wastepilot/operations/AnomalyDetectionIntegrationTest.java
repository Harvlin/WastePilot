package com.project.wastepilot.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.MaterialEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.TemplateLineEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.enums.BatchStatus;
import com.project.wastepilot.domain.enums.CircularGrade;
import com.project.wastepilot.domain.enums.InsightStatus;
import com.project.wastepilot.domain.enums.MaterialCategory;
import com.project.wastepilot.domain.enums.WasteDestination;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.BatchRepository;
import com.project.wastepilot.repository.InsightRepository;
import com.project.wastepilot.repository.MaterialRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.repository.WasteLogRepository;
import com.project.wastepilot.service.AnomalyDetectionService;
import com.project.wastepilot.service.InsightGenerationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AnomalyDetectionIntegrationTest {

  @Autowired
  private AnomalyRepository anomalyRepository;

  @Autowired
  private InsightRepository insightRepository;

  @Autowired
  private WasteLogRepository wasteLogRepository;

  @Autowired
  private BatchRepository batchRepository;

  @Autowired
  private com.project.wastepilot.repository.InventoryLogRepository inventoryLogRepository;

  @Autowired
  private MaterialRepository materialRepository;

  @Autowired
  private TemplateRepository templateRepository;

  @Autowired
  private AnomalyDetectionService anomalyDetectionService;

  @Autowired
  private InsightGenerationService insightGenerationService;

  @BeforeEach
  void setUp() {
    anomalyRepository.deleteAll();
    insightRepository.deleteAll();
    wasteLogRepository.deleteAll();
    inventoryLogRepository.deleteAll();
    batchRepository.deleteAll();
    templateRepository.deleteAll();
    materialRepository.deleteAll();
  }

  @Test
  void shouldGenerateAnomalyWhenWasteIsHigh() {
    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Test Template");
    batch.setStartedAt(Instant.now());
    batch.setOutputUnits(BigDecimal.valueOf(100));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.running);
    batch = batchRepository.save(batch);

    for (int i = 0; i < 20; i++) {
      WasteLogEntity normal = new WasteLogEntity();
      normal.setBatch(batch);
      normal.setMaterialName("Cotton");
      normal.setQuantityKg(BigDecimal.valueOf(10 + (i % 3 - 1))); // 9, 10, 11
      normal.setDestination(WasteDestination.dispose);
      normal.setRecoveryStatus(com.project.wastepilot.domain.enums.RecoveryStatus.not_applicable);
      normal.setTimestamp(Instant.now().minus(i + 1, ChronoUnit.DAYS));
      wasteLogRepository.save(normal);
    }

    WasteLogEntity anomalous = new WasteLogEntity();
    anomalous.setBatch(batch);
    anomalous.setMaterialName("Cotton");
    anomalous.setQuantityKg(BigDecimal.valueOf(20));
    anomalous.setDestination(WasteDestination.dispose);
    anomalous.setRecoveryStatus(com.project.wastepilot.domain.enums.RecoveryStatus.not_applicable);
    anomalous.setTimestamp(Instant.now());
    anomalous = wasteLogRepository.save(anomalous);

    anomalyDetectionService.evaluateWasteLog(anomalous);

    assertThat(anomalyRepository.findAll()).hasSize(1);
    assertThat(anomalyRepository.findAll().get(0).getZScore().doubleValue()).isGreaterThan(2.5);
  }

  @Test
  void shouldGenerateInsightForDisproportionateWaste() {
    MaterialEntity mat1 = new MaterialEntity();
    mat1.setName("Mat 1");
    mat1.setCategory(MaterialCategory.Recyclable);
    mat1.setUnit("kg");
    mat1.setCircularGrade(CircularGrade.A);
    mat1.setStock(BigDecimal.ZERO);
    mat1.setSupplier("Sup");
    mat1 = materialRepository.save(mat1);

    MaterialEntity mat2 = new MaterialEntity();
    mat2.setName("Mat 2");
    mat2.setCategory(MaterialCategory.Recyclable);
    mat2.setUnit("kg");
    mat2.setCircularGrade(CircularGrade.A);
    mat2.setStock(BigDecimal.ZERO);
    mat2.setSupplier("Sup");
    mat2 = materialRepository.save(mat2);

    TemplateEntity template = new TemplateEntity();
    template.setName("Test Template");
    template.setSku("SKU-1");
    template.setExpectedWasteKg(BigDecimal.TEN);
    
    TemplateLineEntity line1 = new TemplateLineEntity();
    line1.setTemplate(template);
    line1.setMaterial(mat1);
    line1.setQuantity(BigDecimal.valueOf(90));
    line1.setUnit("kg");

    TemplateLineEntity line2 = new TemplateLineEntity();
    line2.setTemplate(template);
    line2.setMaterial(mat2);
    line2.setQuantity(BigDecimal.valueOf(10));
    line2.setUnit("kg");
    
    template.setLines(List.of(line1, line2));
    template = templateRepository.save(template);

    BatchEntity batch = new BatchEntity();
    batch.setTemplateName("Test Template");
    batch.setStartedAt(Instant.now());
    batch.setOutputUnits(BigDecimal.valueOf(100));
    batch.setWasteKg(BigDecimal.ZERO);
    batch.setStatus(BatchStatus.completed);
    batch = batchRepository.save(batch);

    WasteLogEntity w1 = new WasteLogEntity();
    w1.setBatch(batch);
    w1.setMaterialName("Mat 1");
    w1.setQuantityKg(BigDecimal.valueOf(50));
    w1.setDestination(WasteDestination.dispose);
    w1.setRecoveryStatus(com.project.wastepilot.domain.enums.RecoveryStatus.not_applicable);
    w1.setTimestamp(Instant.now());
    wasteLogRepository.save(w1);

    WasteLogEntity w2 = new WasteLogEntity();
    w2.setBatch(batch);
    w2.setMaterialName("Mat 2");
    w2.setQuantityKg(BigDecimal.valueOf(50));
    w2.setDestination(WasteDestination.dispose);
    w2.setRecoveryStatus(com.project.wastepilot.domain.enums.RecoveryStatus.not_applicable);
    w2.setTimestamp(Instant.now());
    wasteLogRepository.save(w2);

    insightGenerationService.evaluateAfterBatchClose(batch);

    assertThat(insightRepository.findAll()).hasSize(1);
    assertThat(insightRepository.findAll().get(0).getTitle()).contains("Disproportionate");
  }
}
