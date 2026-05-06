package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.material.TemplateLineRequest;
import com.project.wastepilot.domain.dto.material.TemplateLineResponse;
import com.project.wastepilot.domain.dto.material.TemplateResponse;
import com.project.wastepilot.domain.dto.material.UpsertTemplateRequest;
import com.project.wastepilot.domain.entity.MaterialEntity;
import com.project.wastepilot.domain.entity.TemplateEntity;
import com.project.wastepilot.domain.entity.TemplateLineEntity;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.repository.MaterialRepository;
import com.project.wastepilot.repository.TemplateRepository;
import com.project.wastepilot.service.TemplateService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

  private final TemplateRepository templateRepository;
  private final MaterialRepository materialRepository;

  @Override
  @Transactional(readOnly = true)
  public List<TemplateResponse> getTemplates() {
    return templateRepository.findAll().stream()
        .sorted(Comparator.comparing(TemplateEntity::getUpdatedAt).reversed())
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public TemplateResponse createTemplate(UpsertTemplateRequest request) {
    String normalizedSku = normalizeSku(request.sku());
    if (templateRepository.findBySkuIgnoreCase(normalizedSku).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_SKU_EXISTS", "Template SKU already exists.");
    }

    TemplateEntity template = new TemplateEntity();
    template.setName(request.name().trim());
    template.setSku(normalizedSku);
    template.setExpectedWasteKg(request.expectedWasteKg());
    syncLines(template, request.lines());

    TemplateEntity saved = templateRepository.save(template);
    return toResponse(saved);
  }

  @Override
  @Transactional
  public TemplateResponse updateTemplate(String id, UpsertTemplateRequest request) {
    UUID templateId = parseUuid(id, "templateId");
    TemplateEntity template = templateRepository.findById(templateId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found."));

    String normalizedSku = normalizeSku(request.sku());
    templateRepository.findBySkuIgnoreCase(normalizedSku)
        .filter(existing -> !existing.getId().equals(template.getId()))
        .ifPresent(existing -> {
          throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_SKU_EXISTS", "Template SKU already exists.");
        });

    template.setName(request.name().trim());
    template.setSku(normalizedSku);
    template.setExpectedWasteKg(request.expectedWasteKg());
    syncLines(template, request.lines());

    TemplateEntity saved = templateRepository.save(template);
    return toResponse(saved);
  }

  @Override
  @Transactional
  public void deleteTemplate(String id) {
    UUID templateId = parseUuid(id, "templateId");
    if (!templateRepository.existsById(templateId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found.");
    }
    templateRepository.deleteById(templateId);
  }

  private void syncLines(TemplateEntity template, List<TemplateLineRequest> requestLines) {
    List<TemplateLineEntity> nextLines = new ArrayList<>();
    for (TemplateLineRequest lineRequest : requestLines) {
      UUID materialId = parseUuid(lineRequest.materialId(), "materialId");
      MaterialEntity material = materialRepository.findById(materialId)
          .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MATERIAL_NOT_FOUND", "Material not found."));

      TemplateLineEntity line = new TemplateLineEntity();
      line.setTemplate(template);
      line.setMaterial(material);
      line.setQuantity(lineRequest.quantity());
      line.setUnit(lineRequest.unit().trim());
      nextLines.add(line);
    }

    template.getLines().clear();
    template.getLines().addAll(nextLines);
  }

  private TemplateResponse toResponse(TemplateEntity entity) {
    List<TemplateLineResponse> lines = entity.getLines().stream()
        .map(line -> new TemplateLineResponse(
            line.getMaterial().getId().toString(),
            line.getMaterial().getName(),
            line.getQuantity(),
            line.getUnit()
        ))
        .toList();

    return new TemplateResponse(
        entity.getId().toString(),
        entity.getName(),
        entity.getSku(),
        entity.getExpectedWasteKg(),
        entity.getUpdatedAt(),
        lines
    );
  }

  private String normalizeSku(String sku) {
    return sku.trim().toUpperCase();
  }

  private UUID parseUuid(String value, String fieldName) {
    try {
      return UUID.fromString(value);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UUID", "Invalid " + fieldName + ".");
    }
  }
}
