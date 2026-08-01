package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.ai.OcrMaterialLine;
import com.project.wastepilot.service.GeminiOcrService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class OcrController {

  private final GeminiOcrService geminiOcrService;

  /**
   * POST /api/v1/ai/ocr
   * Accepts a multipart image upload, processes it via Gemini Vision (in-memory),
   * and returns extracted material lines.
   *
   * Content-Type: multipart/form-data
   * Form field: "file" — the image (PNG, JPEG, WebP, max 10 MB)
   */
  @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public List<OcrMaterialLine> scanInvoice(
      @RequestParam("file") MultipartFile file
  ) {
    return geminiOcrService.extractMaterialLines(file);
  }
}
