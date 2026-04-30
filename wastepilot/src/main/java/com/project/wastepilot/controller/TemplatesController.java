package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.material.TemplateResponse;
import com.project.wastepilot.domain.dto.material.UpsertTemplateRequest;
import com.project.wastepilot.service.TemplateService;
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
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplatesController {

  private final TemplateService templateService;

  @GetMapping
  public List<TemplateResponse> getTemplates() {
    return templateService.getTemplates();
  }

  @PostMapping
  public TemplateResponse createTemplate(@Valid @RequestBody UpsertTemplateRequest request) {
    return templateService.createTemplate(request);
  }

  @PutMapping("/{id}")
  public TemplateResponse updateTemplate(@PathVariable String id, @Valid @RequestBody UpsertTemplateRequest request) {
    return templateService.updateTemplate(id, request);
  }

  @DeleteMapping("/{id}")
  public void deleteTemplate(@PathVariable String id) {
    templateService.deleteTemplate(id);
  }
}
