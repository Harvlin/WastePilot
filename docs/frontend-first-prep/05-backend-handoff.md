# Backend Handoff Checklist

This document serves as the guide for backend developers implementing the real APIs for the frontend-first features.

## 1. Sensor Ingestion
- [ ] **Mock Status:** N/A (Frontend doesn't consume this directly, it's machine-to-machine).
- [ ] **Backend Needs:** 
  - Create `SensorIngestController`.
  - Add DB schema `sensor_payloads`.
  - Ensure idempotency (deduplicate by `machineId` + `timestamp`).

## 2. Confidence Score Breakdown
- [ ] **Mock Status:** Mocked in `fetchBatchCloseSummary`.
- [ ] **Backend Needs:**
  - Update `BatchCloseSummaryResponse` DTO to include `ConfidenceBreakdown` object.
  - Implement business logic calculating `completeness`, `timeliness`, and `auditIntegrity` instead of hardcoding the `confidenceScore`.

## 3. Role-Based Access
- [ ] **Mock Status:** Hardcoded `role: "admin"` in `mock-api.ts`.
- [ ] **Backend Needs:**
  - Add `role` column to `users` table (enum: admin, operator, viewer).
  - Update `AuthResponse.UserSession` DTO.
  - Apply Spring Security `@PreAuthorize("hasRole('ADMIN')")` where necessary.

## 4. Live Factory Floor View
- [ ] **Mock Status:** Fully mocked with polling capability.
- [ ] **Backend Needs:**
  - Create `GET /operations/live-floor` endpoint.
  - Requires joining `batches` with real-time sensor aggregates (if available) or inventory logs to calculate `varianceSoFarPercent`.
  - Define logic for `healthIndicator` (e.g. green if variance < 2%, red if > 5%).

## 5. Notification / Alert Center
- [ ] **Mock Status:** Mocked array returned on load.
- [ ] **Backend Needs:**
  - Create `notifications` table (id, user_id, type, title, message, is_read).
  - Create `GET /notifications` and `PATCH /notifications/{id}/read` endpoints.
  - Event listeners to trigger notifications (e.g., when an anomaly is created).

## 6. Predictive Waste Forecasting
- [ ] **Mock Status:** Mocked line chart data.
- [ ] **Backend Needs:**
  - Connect to AI/ML service or run statistical regression on historical `waste_logs`.
  - Create `GET /ai/forecast` returning expected bounds.

## 7. Sustainability Impact
- [ ] **Mock Status:** Mocked static numbers.
- [ ] **Backend Needs:**
  - Create `GET /analytics/sustainability` endpoint.
  - Define conversion factors (e.g., 1kg waste diverted = X kg CO2 saved) in a configuration file or settings table.

## 8. Export (CSV/PDF)
- [ ] **Mock Status:** Simulates file download with a timeout.
- [ ] **Backend Needs:**
  - Implement `POST /reports/export`.
  - Integrate CSV generation (e.g., OpenCSV) and PDF generation (e.g., JasperReports or iText).

## 9. Comparison/Trend Analytics
- [ ] **Mock Status:** Mocked chart data grouping.
- [ ] **Backend Needs:**
  - Implement complex SQL group-by queries to aggregate data across lines, shifts, or time ranges for `GET /analytics/comparison`.
