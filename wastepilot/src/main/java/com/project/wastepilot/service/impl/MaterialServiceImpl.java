package com.project.wastepilot.service.impl;

import com.project.wastepilot.domain.dto.material.MaterialResponse;
import com.project.wastepilot.domain.dto.material.UpsertMaterialRequest;
import com.project.wastepilot.domain.entity.MaterialEntity;
import com.project.wastepilot.domain.enums.CircularGrade;
import com.project.wastepilot.domain.enums.MaterialCategory;
import com.project.wastepilot.exception.ApiException;
import com.project.wastepilot.repository.MaterialRepository;
import com.project.wastepilot.service.MaterialService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

  private static final Map<String, MaterialCategory> EXTERNAL_TO_INTERNAL_CATEGORY = Map.of(
      "RECYCLABLE", MaterialCategory.Recyclable,
      "BIODEGRADABLE", MaterialCategory.Organic,
      "COMPOSITE", MaterialCategory.Hazardous,
      "REUSABLE", MaterialCategory.Packaging
  );

  private static final Map<MaterialCategory, String> INTERNAL_TO_EXTERNAL_CATEGORY = Map.of(
      MaterialCategory.Recyclable, "Recyclable",
      MaterialCategory.Organic, "Biodegradable",
      MaterialCategory.Hazardous, "Composite",
      MaterialCategory.Packaging, "Reusable"
  );

  private final MaterialRepository materialRepository;

  @Override
  @Transactional(readOnly = true)
  public List<MaterialResponse> getMaterials() {
    return materialRepository.findAll().stream()
        .sorted(Comparator.comparing(MaterialEntity::getUpdatedAt).reversed())
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public MaterialResponse createMaterial(UpsertMaterialRequest request) {
    String normalizedName = request.name().trim();
    if (materialRepository.existsByNameIgnoreCase(normalizedName)) {
      throw new ApiException(HttpStatus.CONFLICT, "MATERIAL_NAME_EXISTS", "Material name already exists.");
    }

    MaterialEntity material = new MaterialEntity();
    applyRequest(material, request, normalizedName);
    MaterialEntity saved = materialRepository.save(material);
    return toResponse(saved);
  }

  @Override
  @Transactional
  public MaterialResponse updateMaterial(String id, UpsertMaterialRequest request) {
    UUID materialId = parseUuid(id, "materialId");
    MaterialEntity material = materialRepository.findById(materialId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND", "Material not found."));

    String normalizedName = request.name().trim();
    materialRepository.findByNameIgnoreCase(normalizedName)
        .filter(existing -> !existing.getId().equals(materialId))
        .ifPresent(existing -> {
          throw new ApiException(HttpStatus.CONFLICT, "MATERIAL_NAME_EXISTS", "Material name already exists.");
        });

    applyRequest(material, request, normalizedName);
    MaterialEntity saved = materialRepository.save(material);
    return toResponse(saved);
  }

  @Override
  @Transactional
  public void deleteMaterial(String id) {
    UUID materialId = parseUuid(id, "materialId");
    if (!materialRepository.existsById(materialId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND", "Material not found.");
    }
    try {
      materialRepository.deleteById(materialId);
    } catch (DataIntegrityViolationException ex) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "MATERIAL_IN_USE",
          "Material cannot be deleted because it is still used by templates or logs."
      );
    }
  }

  private void applyRequest(MaterialEntity material, UpsertMaterialRequest request, String normalizedName) {
    material.setName(normalizedName);
    material.setCategory(parseCategory(request.category()));
    material.setUnit(request.unit().trim());
    material.setCircularGrade(parseCircularGrade(request.circularGrade()));
    material.setStock(request.stock());
    material.setSupplier(request.supplier().trim());
  }

  private MaterialResponse toResponse(MaterialEntity entity) {
    return new MaterialResponse(
        entity.getId().toString(),
        entity.getName(),
        INTERNAL_TO_EXTERNAL_CATEGORY.getOrDefault(entity.getCategory(), "Recyclable"),
        entity.getUnit(),
        entity.getCircularGrade().name(),
        entity.getStock(),
        entity.getSupplier()
    );
  }

  private MaterialCategory parseCategory(String rawCategory) {
    String normalized = rawCategory == null ? "" : rawCategory.trim().toUpperCase();
    MaterialCategory category = EXTERNAL_TO_INTERNAL_CATEGORY.get(normalized);
    if (category == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Material category is invalid.");
    }
    return category;
  }

  private CircularGrade parseCircularGrade(String rawGrade) {
    try {
      return CircularGrade.valueOf(rawGrade.trim().toUpperCase());
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CIRCULAR_GRADE", "Circular grade is invalid.");
    }
  }

  private UUID parseUuid(String value, String fieldName) {
    try {
      return UUID.fromString(value);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UUID", "Invalid " + fieldName + ".");
    }
  }
}
