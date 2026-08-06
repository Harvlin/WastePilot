# API Contracts for New Features

All endpoints are prefixed with `/api/v1/`.

## 1. Sensor Ingestion
*Note: Pure backend endpoint for IoT devices to push data, but documented for consistency.*
- **Endpoint:** `POST /operations/sensors/ingest`
- **Auth:** Requires a valid API Key or machine-to-machine JWT.
- **Request Body:**
```typescript
interface SensorIngestRequest {
  machineId: string;
  sensorType: "weight" | "counter";
  value: number;
  unit: string;
  timestamp: string;
}
```
```json
{
  "machineId": "CUT-LINE-A",
  "sensorType": "weight",
  "value": 14.5,
  "unit": "kg",
  "timestamp": "2026-08-01T10:00:00Z"
}
```
- **Response:** `200 OK`
```json
{
  "message": "Payload ingested successfully"
}
```

## 2. Confidence Score Breakdown
*Extension of existing `/operations/batch-close/summary/{batchId}`.*
- **Endpoint:** `GET /operations/batch-close/summary/{batchId}`
- **Auth:** Standard Bearer token.
- **Response Shape (Existing + Extended):**
```typescript
interface BatchCloseSummary {
  batchId: string;
  templateName: string;
  // ... existing fields ...
  actualInputKg: number;
  actualInputSource: "measured" | "estimated";
  confidenceScore: number;
  confidenceLevel: "high" | "medium" | "low";
  // EXTENSION:
  confidenceBreakdown: {
    completeness: number; // 0-100
    timeliness: number; // 0-100
    auditIntegrity: number; // 0-100
  };
}
```

## 3. Role-Based Access
*Extension of existing `/auth/me`.*
- **Endpoint:** `GET /auth/me`
- **Response Shape (Extended):**
```typescript
interface AuthUser {
  id: string;
  fullName: string;
  email: string;
  // EXTENSION:
  role: "admin" | "operator" | "viewer";
  permissions: string[];
}
```
```json
{
  "id": "usr-123",
  "fullName": "Jane Doe",
  "email": "jane@wastepilot.io",
  "role": "admin",
  "permissions": ["manage_settings", "close_batch", "delete_material"]
}
```

## 4. Live Factory Floor View
- **Endpoint:** `GET /operations/live-floor`
- **Query Params:** None (designed for fast polling initially, easily convertible to SSE).
- **Response:**
```typescript
interface LiveFloorPayload {
  activeBatches: Array<{
    batchId: string;
    templateName: string;
    lineId: string;
    status: "running" | "paused" | "anomalous";
    runningTimeMinutes: number;
    varianceSoFarPercent: number;
    healthIndicator: "green" | "amber" | "red";
  }>;
  totalActiveLines: number;
  lastUpdated: string; // ISO
}
```
```json
{
  "activeBatches": [
    {
      "batchId": "B-105",
      "templateName": "Cotton Tee v3",
      "lineId": "LINE-A",
      "status": "running",
      "runningTimeMinutes": 125,
      "varianceSoFarPercent": 2.4,
      "healthIndicator": "green"
    }
  ],
  "totalActiveLines": 1,
  "lastUpdated": "2026-08-01T12:05:00Z"
}
```

## 5. Notification / Alert Center
- **Endpoint:** `GET /notifications`
- **Query Params:** `?unreadOnly=true&limit=20`
- **Response:**
```typescript
interface NotificationPayload {
  id: string;
  type: "anomaly" | "insight" | "system" | "integrity";
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
  actionUrl?: string; // Where the user should go
}
// Returns NotificationPayload[] directly.
```

- **Endpoint:** `PATCH /notifications/{id}/read`
- **Request Body:** None.
- **Response:** `204 No Content`

## 6. Predictive Waste Forecasting
- **Endpoint:** `GET /ai/forecast`
- **Query Params:** `?horizonDays=7&materialId=MAT-1` (optional filters)
- **Response:**
```typescript
interface ForecastPayload {
  target: string; // e.g., "Total Waste" or "Cotton Scraps"
  predictions: Array<{
    date: string;
    predictedKg: number;
    lowerBoundKg: number;
    upperBoundKg: number;
  }>;
  keyInputs: string[]; // e.g., ["Historical volume", "Upcoming schedule"]
}
```

## 7. Sustainability Impact
- **Endpoint:** `GET /analytics/sustainability`
- **Response:**
```typescript
interface SustainabilityImpact {
  co2SavedKg: number;
  landfillDivertedKg: number;
  waterSavedLiters: number;
  equivalents: {
    treesPlanted: number;
    carsOffRoad: number;
  };
  conversionAssumptions: string[];
}
```

## 8. Export (CSV/PDF)
- **Endpoint:** `POST /reports/export`
- **Request Body:**
```typescript
interface ExportRequest {
  format: "csv" | "pdf";
  type: "batch_summary" | "audit_trail";
  dateRange?: { start: string; end: string };
  batchId?: string;
}
```
- **Response:** `200 OK` returning standard file blobs (e.g., `application/pdf` or `text/csv`). The JSON fallback error follows the standard `ApiErrorResponse`.

## 9. Comparison/Trend Analytics
- **Endpoint:** `GET /analytics/comparison`
- **Query Params:** `?dimension=line&dateRange=last30days`
- **Response:**
```typescript
interface ComparisonPayload {
  dimension: string;
  series: Array<{
    name: string; // e.g. "Line A"
    data: Array<{
      date: string; // or period like "Week 1"
      value: number; // circular score or waste variance
    }>;
  }>;
}
```

---
### Dependency Flags
- **Backend Logic Does Not Exist Yet:** Sensor ingestion, Live Factory Floor (needs new DB schemas/tracking logic), Notification Center (needs new Notification tables), Forecasting (requires actual AI/stat models), Sustainability Impact (requires conversion logic), Export (requires file generation logic).
- **Simple Extensions:** Confidence Score Breakdown (extends existing logic), Role-based access (adds column to User), Comparison Analytics (groups existing data differently).
