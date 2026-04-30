package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.insight.AnomalyResponse;
import com.project.wastepilot.domain.dto.insight.CircularInsightResponse;
import com.project.wastepilot.domain.entity.AnomalyEntity;
import com.project.wastepilot.domain.entity.InsightEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface AiMapper {
    AnomalyResponse toResponse(AnomalyEntity entity);
    CircularInsightResponse toResponse(InsightEntity entity);
}
