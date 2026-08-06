package com.project.wastepilot.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.project.wastepilot.domain.dto.analytics.ReportsPayloadResponse;
import com.project.wastepilot.domain.dto.analytics.ReportSummaryResponse;
import com.project.wastepilot.domain.dto.analytics.ReportTrendPoint;
import com.project.wastepilot.service.ExportService;
import com.project.wastepilot.service.ReportsService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ReportsService reportsService;

    @Override
    public byte[] exportCsv(String period) {
        ReportsPayloadResponse payload = reportsService.getReportsPayload(period);
        StringBuilder csv = new StringBuilder();

        // CSV Header
        csv.append("Label,Transactions,Input(kg),Output(kg),Waste(kg),Recovered(kg),Landfill(kg),Circular Score\n");

        for (ReportTrendPoint point : payload.trend()) {
            csv.append(escapeCsv(point.label())).append(",");
            csv.append(point.transactions()).append(",");
            csv.append("-").append(","); // Input isn't in trend point explicitly but we'll leave a dash
            csv.append("-").append(","); // Output isn't in trend point
            csv.append(point.wasteKg() != null ? point.wasteKg().toPlainString() : "0").append(",");
            csv.append(point.recoveredKg() != null ? point.recoveredKg().toPlainString() : "0").append(",");
            csv.append(point.landfillKg() != null ? point.landfillKg().toPlainString() : "0").append(",");
            csv.append(point.circularScore() != null ? point.circularScore().toPlainString() : "0").append("\n");
        }

        // Add a summary row
        ReportSummaryResponse summary = payload.summary();
        csv.append("SUMMARY").append(",");
        csv.append(summary.totalActivities() + summary.completedBatches()).append(","); // approximation
        csv.append(summary.totalInventoryIn() != null ? summary.totalInventoryIn().toPlainString() : "0").append(",");
        csv.append(summary.totalInventoryOut() != null ? summary.totalInventoryOut().toPlainString() : "0").append(",");
        csv.append(summary.totalWasteKg() != null ? summary.totalWasteKg().toPlainString() : "0").append(",");
        csv.append(summary.recoveredWasteKg() != null ? summary.recoveredWasteKg().toPlainString() : "0").append(",");
        csv.append(summary.landfillWasteKg() != null ? summary.landfillWasteKg().toPlainString() : "0").append(",");
        csv.append(summary.circularScoreAvg() != null ? summary.circularScoreAvg().toPlainString() : "0").append("\n");

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportPdf(String period) {
        ReportsPayloadResponse payload = reportsService.getReportsPayload(period);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (Document document = new Document()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

            Paragraph title = new Paragraph("WastePilot Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            Paragraph subtitle = new Paragraph(payload.windowLabel(), normalFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // Summary Table
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingAfter(20);
            
            ReportSummaryResponse summary = payload.summary();
            addTableRow(summaryTable, "Generated At", payload.generatedAt(), headerFont, normalFont);
            addTableRow(summaryTable, "Total Waste (kg)", summary.totalWasteKg() != null ? summary.totalWasteKg().toPlainString() : "0", headerFont, normalFont);
            addTableRow(summaryTable, "Recovered (kg)", summary.recoveredWasteKg() != null ? summary.recoveredWasteKg().toPlainString() : "0", headerFont, normalFont);
            addTableRow(summaryTable, "Landfill (kg)", summary.landfillWasteKg() != null ? summary.landfillWasteKg().toPlainString() : "0", headerFont, normalFont);
            addTableRow(summaryTable, "Average Circular Score", summary.circularScoreAvg() != null ? summary.circularScoreAvg().toPlainString() : "0", headerFont, normalFont);
            
            document.add(summaryTable);

            // Highlights
            if (payload.highlights() != null && !payload.highlights().isEmpty()) {
                Paragraph highlightTitle = new Paragraph("Highlights", headerFont);
                highlightTitle.setSpacingAfter(10);
                document.add(highlightTitle);

                com.lowagie.text.List list = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
                for (String h : payload.highlights()) {
                    list.add(new com.lowagie.text.ListItem(h, normalFont));
                }
                document.add(list);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }

        return out.toByteArray();
    }

    private void addTableRow(PdfPTable table, String header, String value, Font headerFont, Font valueFont) {
        PdfPCell headerCell = new PdfPCell(new Phrase(header, headerFont));
        headerCell.setPadding(5);
        table.addCell(headerCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private String escapeCsv(String data) {
        if (data == null) {
            return "";
        }
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }
}
