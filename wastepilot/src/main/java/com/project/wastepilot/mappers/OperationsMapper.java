package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.operations.BatchResponse;
import com.project.wastepilot.domain.dto.operations.InventoryLogResponse;
import com.project.wastepilot.domain.dto.operations.WasteLogResponse;
import com.project.wastepilot.domain.dto.material.TemplateResponse;
import com.project.wastepilot.domain.dto.material.TemplateLineResponse;
import com.project.wastepilot.domain.entity.BatchEntity;
import com.project.wastepilot.domain.entity.InventoryLogEntity;
import com.project.wastepilot.domain.entity.WasteLogEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.TemplateLineEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface OperationsMapper {
    BatchResponse toResponse(BatchEntity entity);
    @Mapping(source = "batch.id", target = "batchId")
    InventoryLogResponse toResponse(InventoryLogEntity entity);
    
    @Mapping(source = "batch.id", target = "batchId")
    WasteLogResponse toResponse(WasteLogEntity entity);
    
    @Mapping(target = "lines", ignore = true)
    TemplateResponse toResponse(TemplateEntity entity);
    
    @Mapping(source = "material.id", target = "materialId")
    @Mapping(source = "material.name", target = "materialName")
    TemplateLineResponse toResponse(TemplateLineEntity entity);
}
