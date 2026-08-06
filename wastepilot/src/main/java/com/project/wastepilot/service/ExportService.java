package com.project.wastepilot.service;

public interface ExportService {
    byte[] exportCsv(String period);
    byte[] exportPdf(String period);
}
