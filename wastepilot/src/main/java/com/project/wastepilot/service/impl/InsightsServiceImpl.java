package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.ai.AnomalyResponse;
import com.project.wastepilot.domain.dto.ai.InsightResponse;
import com.project.wastepilot.domain.entity.AnomalyEntity;
import com.project.wastepilot.domain.entity.InsightEntity;
import com.project.wastepilot.domain.enums.InsightStatus;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.repository.AnomalyRepository;
import com.project.wastepilot.repository.InsightRepository;
import com.project.wastepilot.service.InsightsService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InsightsServiceImpl implements InsightsService {

  private final InsightRepository insightRepository;
  private final AnomalyRepository anomalyRepository;


  @Override
  @Transactional(readOnly = true)
  public List<InsightResponse> getAllInsights() {
    return insightRepository.findAllByOrderByTimestampDesc().stream()
        .map(this::toInsightResponse)
        .toList();
  }

  @Override
  @Transactional
  public InsightResponse updateInsightStatus(UUID id, String status) {
    InsightStatus newStatus = parseStatus(status);
    InsightEntity entity = insightRepository.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSIGHT_NOT_FOUND",
            "Insight with ID " + id + " not found."));
    entity.setStatus(newStatus);
    return toInsightResponse(insightRepository.save(entity));
  }


  @Override
  @Transactional(readOnly = true)
  public List<AnomalyResponse> getAllAnomalies() {
    return anomalyRepository.findAllByOrderByTimestampDesc().stream()
        .map(this::toAnomalyResponse)
        .toList();
  }

  @Override
  @Transactional
  public AnomalyResponse updateAnomalyStatus(UUID id, String status) {
    InsightStatus newStatus = parseStatus(status);
    AnomalyEntity entity = anomalyRepository.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANOMALY_NOT_FOUND",
            "Anomaly with ID " + id + " not found."));
    entity.setStatus(newStatus);
    return toAnomalyResponse(anomalyRepository.save(entity));
  }


  private InsightResponse toInsightResponse(InsightEntity e) {
    return new InsightResponse(
        e.getId().toString(),
        e.getTitle(),
        e.getContent(),
        e.getImpactCategory(),
        e.getStatus().toValue(),
        e.getTimestamp()
    );
  }

  private AnomalyResponse toAnomalyResponse(AnomalyEntity e) {
    return new AnomalyResponse(
        e.getId().toString(),
        e.getDate(),
        e.getProcess(),
        e.getZScore(),
        e.getWasteKg(),
        e.getStatus().toValue(),
        e.getNote()
    );
  }


  private InsightStatus parseStatus(String status) {
    try {
      return InsightStatus.fromValue(status);
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
          "Status must be one of: new, applied, ignored.");
    }
  }
}
