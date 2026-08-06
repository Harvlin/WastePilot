package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.analytics.ReportsPayloadResponse;
import com.project.wastepilot.service.ExportService;
import com.project.wastepilot.service.ReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportsController {

  private final ReportsService reportsService;
  private final ExportService exportService;

  /**
   * GET /api/v1/reports?period=weekly
   * GET /api/v1/reports?period=monthly
   */
  @GetMapping
  public ReportsPayloadResponse getReports(
      @RequestParam(name = "period", defaultValue = "weekly") String period
  ) {
    return reportsService.getReportsPayload(period);
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> exportReports(
      @RequestParam(name = "format", defaultValue = "csv") String format,
      @RequestParam(name = "period", defaultValue = "weekly") String period
  ) {
    if ("pdf".equalsIgnoreCase(format)) {
      byte[] pdf = exportService.exportPdf(period);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", "reports.pdf");
      return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    } else {
      byte[] csv = exportService.exportCsv(period);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType("text/csv"));
      headers.setContentDispositionFormData("attachment", "reports.csv");
      return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }
  }
}
