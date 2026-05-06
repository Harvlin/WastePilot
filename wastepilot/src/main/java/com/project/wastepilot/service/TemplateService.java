package com.project.wastepilot.service;

import com.project.wastepilot.domain.dto.material.TemplateLineResponse;
import com.project.wastepilot.domain.dto.material.TemplateResponse;
import com.project.wastepilot.domain.dto.material.UpsertTemplateRequest;
import java.util.List;

public interface TemplateService {
    List<TemplateResponse> getTemplates();
    TemplateResponse createTemplate(UpsertTemplateRequest request);
    TemplateResponse updateTemplate(String id, UpsertTemplateRequest request);
    void deleteTemplate(String id);
}
