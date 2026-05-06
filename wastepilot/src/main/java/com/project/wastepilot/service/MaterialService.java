package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.material.MaterialResponse;
import com.project.wastepilot.domain.dto.material.UpsertMaterialRequest;
import java.util.List;

public interface MaterialService {
    List<MaterialResponse> getMaterials();
    MaterialResponse createMaterial(UpsertMaterialRequest request);
    MaterialResponse updateMaterial(String id, UpsertMaterialRequest request);
    void deleteMaterial(String id);
}
