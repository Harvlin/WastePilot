package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.material.MaterialResponse;
import com.project.wastepilot.domain.dto.material.UpsertMaterialRequest;
import com.project.wastepilot.service.MaterialService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
public class MaterialsController {

  private final MaterialService materialService;

  @GetMapping
  public List<MaterialResponse> getMaterials() {
    return materialService.getMaterials();
  }

  @PostMapping
  public MaterialResponse createMaterial(@Valid @RequestBody UpsertMaterialRequest request) {
    return materialService.createMaterial(request);
  }

  @PutMapping("/{id}")
  public MaterialResponse updateMaterial(@PathVariable String id, @Valid @RequestBody UpsertMaterialRequest request) {
    return materialService.updateMaterial(id, request);
  }

  @DeleteMapping("/{id}")
  public void deleteMaterial(@PathVariable String id) {
    materialService.deleteMaterial(id);
  }
}
