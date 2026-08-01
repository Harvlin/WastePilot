package com.project.wastepilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wastepilot.config.GeminiProperties;
import com.project.wastepilot.domain.dto.ai.OcrMaterialLine;
import com.project.wastepilot.exception.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Calls the Google Gemini Vision API to extract material lines from an uploaded invoice/receipt image.
 * Uses in-memory processing — no file is persisted to disk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiOcrService {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  // JSON Schema prompt sent to Gemini. Forces it to respond with a strict JSON array.
  private static final String OCR_PROMPT = """
      You are an OCR assistant specialized in extracting purchasing data from factory invoices and delivery receipts.

      Analyze this image and extract all material/item lines.
      Return ONLY a valid JSON array (no markdown, no code blocks, no explanation) with this exact schema:
      [
        {
          "materialName": "string – name of the material or item",
          "quantity": number – numeric quantity,
          "unit": "string – unit of measure (kg, pcs, liter, box, etc.)",
          "price": number – unit price in local currency (0 if not visible)
        }
      ]

      Rules:
      - If a field is not visible, use empty string for text or 0 for numbers.
      - Do NOT include currency symbols in price.
      - Return an empty array [] if no material lines are found.
      - Output ONLY the JSON array. Nothing else.
      """;

  private final GeminiProperties geminiProperties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(TIMEOUT)
      .build();

  /**
   * Process an uploaded image through Gemini Vision and return extracted material lines.
   * File is processed entirely in memory — no disk I/O.
   *
   * @param file the uploaded invoice/receipt image
   * @return list of extracted OcrMaterialLine items
   */
  public List<OcrMaterialLine> extractMaterialLines(MultipartFile file) {
    validateFile(file);

    String base64Image;
    String mimeType;
    try {
      base64Image = Base64.getEncoder().encodeToString(file.getBytes());
      mimeType = resolveMimeType(file.getContentType());
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR",
          "Failed to read uploaded file.");
    }

    String requestBody = buildGeminiRequestBody(base64Image, mimeType);
    String responseText = callGeminiApi(requestBody);
    return parseOcrResponse(responseText);
  }

  // ── Gemini API call ─────────────────────────────────────────────────────────

  private String callGeminiApi(String requestBody) {
    String url = String.format("%s/%s:generateContent?key=%s",
        geminiProperties.baseUrl(),
        geminiProperties.model(),
        geminiProperties.apiKey());

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .timeout(TIMEOUT)
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 400) {
        log.error("Gemini API bad request: {}", response.body());
        throw new ApiException(HttpStatus.BAD_REQUEST, "GEMINI_BAD_REQUEST",
            "Invalid request to Gemini API. Check image format.");
      }
      if (response.statusCode() == 401 || response.statusCode() == 403) {
        log.error("Gemini API auth error: {}", response.body());
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_AUTH_ERROR",
            "AI service authentication failed. Check your API key.");
      }
      if (response.statusCode() != 200) {
        log.error("Gemini API error {}: {}", response.statusCode(), response.body());
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_ERROR",
            "AI service returned an unexpected error.");
      }

      return extractTextFromGeminiResponse(response.body());

    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_TIMEOUT",
          "Request to AI service was interrupted.");
    } catch (IOException e) {
      log.error("Gemini API IO error", e);
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_UNAVAILABLE",
          "AI service is currently unavailable.");
    }
  }

  // ── Request / Response builders ─────────────────────────────────────────────

  private String buildGeminiRequestBody(String base64Image, String mimeType) {
    try {
      var body = objectMapper.createObjectNode();
      var contents = objectMapper.createArrayNode();
      var content = objectMapper.createObjectNode();
      var parts = objectMapper.createArrayNode();

      // Text part — the OCR prompt
      var textPart = objectMapper.createObjectNode();
      textPart.put("text", OCR_PROMPT);
      parts.add(textPart);

      // Image part — inline base64
      var imagePart = objectMapper.createObjectNode();
      var inlineData = objectMapper.createObjectNode();
      inlineData.put("mimeType", mimeType);
      inlineData.put("data", base64Image);
      imagePart.set("inlineData", inlineData);
      parts.add(imagePart);

      content.set("parts", parts);
      contents.add(content);
      body.set("contents", contents);

      // Enforce JSON output via generationConfig
      var generationConfig = objectMapper.createObjectNode();
      generationConfig.put("responseMimeType", "application/json");
      body.set("generationConfig", generationConfig);

      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build Gemini request body", e);
    }
  }

  private String extractTextFromGeminiResponse(String rawResponse) {
    try {
      JsonNode root = objectMapper.readTree(rawResponse);
      JsonNode candidates = root.path("candidates");
      if (!candidates.isArray() || candidates.isEmpty()) {
        log.warn("Gemini returned no candidates: {}", rawResponse);
        return "[]";
      }
      String text = candidates.get(0)
          .path("content")
          .path("parts")
          .get(0)
          .path("text")
          .asText("[]");
      return text.isBlank() ? "[]" : text;
    } catch (Exception e) {
      log.error("Failed to parse Gemini response structure: {}", rawResponse, e);
      return "[]";
    }
  }

  // ── OCR result parsing ──────────────────────────────────────────────────────

  private List<OcrMaterialLine> parseOcrResponse(String jsonText) {
    try {
      // Strip any accidental markdown fences (safety net even with responseMimeType)
      String cleaned = jsonText.trim()
          .replaceAll("(?s)```json\\s*", "")
          .replaceAll("(?s)```\\s*", "")
          .trim();

      JsonNode array = objectMapper.readTree(cleaned);
      if (!array.isArray()) {
        log.warn("Gemini OCR response is not an array: {}", cleaned);
        return List.of();
      }

      List<OcrMaterialLine> result = new ArrayList<>();
      for (JsonNode node : array) {
        String materialName = node.path("materialName").asText("").trim();
        if (materialName.isBlank()) continue; // skip empty entries

        result.add(new OcrMaterialLine(
            UUID.randomUUID().toString(),
            materialName,
            node.path("quantity").asDouble(0),
            node.path("unit").asText("pcs").trim(),
            node.path("price").asDouble(0)
        ));
      }
      return result;
    } catch (Exception e) {
      log.error("Failed to parse Gemini OCR JSON: {}", jsonText, e);
      return List.of();
    }
  }

  // ── Validation ──────────────────────────────────────────────────────────────

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Uploaded file is empty.");
    }

    String contentType = file.getContentType();
    if (contentType == null || !isSupportedImageType(contentType)) {
      throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
          "Only PNG, JPEG, and WebP images are supported.");
    }

    long maxBytes = 10L * 1024 * 1024; // 10 MB
    if (file.getSize() > maxBytes) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
          "File exceeds the 10 MB limit.");
    }
  }

  private boolean isSupportedImageType(String contentType) {
    return switch (contentType.toLowerCase()) {
      case "image/png", "image/jpeg", "image/jpg", "image/webp" -> true;
      default -> false;
    };
  }

  private String resolveMimeType(String contentType) {
    if (contentType == null) return "image/jpeg";
    return switch (contentType.toLowerCase()) {
      case "image/png" -> "image/png";
      case "image/webp" -> "image/webp";
      default -> "image/jpeg";
    };
  }
}
