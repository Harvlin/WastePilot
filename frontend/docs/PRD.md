# Product Requirements Document (PRD)

## 1. Document Control

- Product: WastePilot
- Document version: v2.0 (implementation-aligned)
- Date: 2026-04-20
- Status: Active working PRD for frontend MVP plus backend implementation handoff
- Owners: Product, Frontend Engineering, Backend Engineering

## 2. Product Overview

### 2.1 Product Summary

WastePilot is a circular operations workspace for manufacturing teams.

It provides:
- Structured operational execution (batch start, inventory movement, waste classification, batch close)
- OCR-assisted intake from invoice images
- Action-oriented dashboard and insights workflow
- Analytics and period reporting views
- Data integrity overlays (activity logs, audit trail, confidence and red flags)

Current stage:
- Frontend MVP is production-like in interaction quality
- Data is mock-backed by default, with typed contract support for Spring Boot integration

### 2.2 Product Stage

- Stage: MVP (frontend-complete for demo and backend integration)
- Data mode: mock default, spring mode supported via API adapter
- Authentication mode: mock session only (not production security)

## 3. Problem Statement

Manufacturing teams often run circularity initiatives with fragmented tools, delayed reporting, and poor traceability across batch, inventory, and waste records.

Key pain points:
- Waste and recovery decisions are delayed because data is spread out
- Operators must manually infer priorities from raw logs
- Circular performance reports are hard to trust without integrity context
- OCR intake is available in tools but often disconnected from operational traceability

WastePilot addresses this by centralizing operational data, adding guided action surfaces, and preparing a clean contract between frontend and backend.

## 4. Product Goals And Non-Goals

### 4.1 Goals

- Centralize circular operations workflows in a single interface.
- Ensure all core flows are backend-ready through typed API contracts.
- Improve data quality with frontend validation for operator input.
- Surface decision priority through Mission Control and Action Queue.
- Make trend and period reporting available in near real-time UI workflows.
- Keep frontend architecture provider-agnostic (mock or spring).

### 4.2 Non-Goals (Current Scope)

- Enterprise identity provider integration (OIDC/SAML) in frontend MVP.
- Multi-tenant authorization and policy enforcement.
- Real-time stream processing or event sourcing architecture.
- ERP replacement or financial accounting workflows.
- Automated ML model training lifecycle.

## 5. Users And Personas

### 5.1 Operations Manager

Needs:
- Batch health and closure discipline
- Waste and recovery visibility
- Trustable score context and red-flag awareness

Primary modules:
- Dashboard, Operations, Analytics

### 5.2 Production Lead

Needs:
- Fast input workflows during shift
- Anomaly-driven intervention priorities
- Immediate correction loops

Primary modules:
- Operations, Insights, Scan

### 5.3 Sustainability Program Owner

Needs:
- Circular performance trends and highlights
- Recommendation adoption visibility
- Reporting windows (weekly/monthly)

Primary modules:
- Dashboard, Insights, Analytics

## 6. Current Product Scope

### 6.1 Public Experience

- Marketing landing with sections: home, how-it-works, features, stats, faq
- CTA routes users to auth or workspace based on auth state
- Mobile navigation with overlay and body scroll locking

### 6.2 Protected Workspace Experience

Workspace routes:
- /dashboard
- /scan
- /materials
- /templates
- /operations
- /insights
- /analytics
- /how-to-use
- /settings

All workspace routes are gated by mock auth.

### 6.3 Authentication (Current)

- Mock auth stored in localStorage
- Session expiration at 8 hours
- Social sign-in actions are mocked
- Protected route redirects to /auth when unauthenticated

### 6.4 Dashboard

Implemented capabilities:
- Circular gauge
- Mission Control queue with prioritized actions
- KPI cards
- Waste trend chart
- Anomaly highlight
- Insights preview cards
- Data Integrity Pulse (avg confidence, open red flags, overdue closures)
- Score methodology explanation text

### 6.5 Operations

Implemented capabilities:
- Create batch with output and expected waste
- Inventory IN/OUT logging (OUT requires running batch)
- Waste logging bound to running batch
- Waste destination classification (reuse, repair, dispose)
- Optional auto-convert waste to inventory IN for reuse/repair
- Batch close assistant with:
  - Auto summary fetch
  - Variance threshold validation
  - Reason required above threshold
  - Confirmation dialog before close
- Integrity tab with activity logs and audit trail
- Overdue running-batch warning banner

### 6.6 Scan (OCR)

Implemented capabilities:
- Upload image or capture via camera input
- OCR request flow
- Editable OCR lines (material, quantity, unit, price)
- Save OCR lines as inventory IN logs
- Required running-batch selection before save
- Runtime validation that selected batch is still running at submission

### 6.7 Materials

Implemented capabilities:
- List materials (category, grade, stock, supplier)
- Add/edit material modal
- Per-material and all-material stock history view from inventory logs

### 6.8 Templates

Implemented capabilities:
- List production templates
- Add/edit template metadata
- Add/remove template material lines
- Expected waste per template

### 6.9 Insights

Implemented capabilities:
- Recommendations and anomalies tabs
- Status updates (new/applied/ignored)
- Action Queue prioritization across recommendations and anomalies
- Bulk apply new recommendations
- Resolve top anomaly shortcut

### 6.10 Analytics

Implemented capabilities:
- Circularity trend
- Waste breakdown
- Material efficiency
- Landfill share trend
- Landfill intensity trend
- Period report section (weekly/monthly):
  - Window label
  - Summary cards
  - Circular score trend
  - Waste flow trend
  - Top actions
  - Top contributors
  - Highlights

### 6.11 Guide

Implemented capabilities:
- Quick start flow
- First-day checklist
- Field cheatsheet

Design intent for new users:
- Guide content is written for first-time operators, not only for expert users.
- Instructions must use plain language, one action per line, and consistent terms with UI labels.
- Every step should clearly answer: what to do, why it matters, and what output to expect.

Beginner-friendly workflow in /how-to-use:
- Step 1 - Login and orientation (2-3 minutes)
  - Confirm workspace access and identify the main menu items.
  - Understand where daily operations happen: Operations, Scan, Insights, Analytics.
- Step 2 - Start shift and open batch (3-5 minutes)
  - Create or select a batch before recording production movement.
  - Verify expected output and expected waste before continuing.
- Step 3 - Record material movement during shift (ongoing)
  - Log inventory IN and OUT with complete material name and quantity.
  - Use OCR scan when invoice data exists to reduce manual typing.
- Step 4 - Record waste with destination (ongoing)
  - Log waste as reuse, repair, or dispose based on actual condition.
  - Use recovery conversion for reuse and repair when material should re-enter stock.
- Step 5 - Close batch at end of shift (2-4 minutes)
  - Review close summary and variance.
  - Provide close reason if variance exceeds threshold.
  - Confirm close after re-checking quantities.
- Step 6 - Review integrity and priorities (2-3 minutes)
  - Check Integrity tab for red flags, confidence, and audit trail.
  - Continue with Action Queue in Dashboard or Insights for follow-up tasks.

First-week enablement checklist:
- Day 1: Complete one full batch lifecycle with supervisor support.
- Day 2-3: Use OCR for at least one inbound record per shift.
- Day 4-5: Resolve at least one recommendation and one anomaly.
- End of week: Review analytics window and discuss top contributors and top actions.

Common mistakes and prevention:
- Mistake: logging OUT without selecting running batch.
  - Prevention: always confirm active running batch in Operations before submission.
- Mistake: closing batch with unexplained high variance.
  - Prevention: validate output units and document close reason early.
- Mistake: treating guide as read-once document.
  - Prevention: use quick cheatsheet during each shift handover until routine is stable.

### 6.12 Settings

Implemented capabilities:
- Edit company name, email, role
- Notification toggles
- Daily token budget with bounded validation
- Time display note set to local device time

Decision:
- Timezone selection UI is intentionally de-scoped for now.
- Frontend displays local device time and backend should keep canonical UTC timestamps.

## 7. Functional Requirements

### 7.1 Routing And Access

- Public routes must render without auth.
- Protected routes must require authentication.
- Route-level lazy loading is required.

### 7.2 Data Fetching And Error States

- Every core page must provide loading, empty, and retry-capable error states.
- API failures must show user-facing feedback (toast and/or error component).

### 7.3 Validation Rules (Frontend)

- Batch creation:
  - templateName required
  - outputUnits > 0
  - estimatedWaste >= 0
- Inventory log:
  - material required
  - quantity > 0
  - OUT requires running batch selection
- Waste log:
  - running batch required
  - selected batch must still be running at submit
  - material required
  - quantity > 0
- Batch close:
  - running batch required
  - outputUnits > 0
  - close reason required when |variance| > CLOSE_VARIANCE_THRESHOLD
- OCR rows:
  - material non-empty
  - quantity >= 0.01
  - unit non-empty
  - price > 0
  - selected batch still running at save time
- Settings:
  - company non-empty
  - valid email
  - dailyTokenBudget within configured bounds

### 7.4 Integrity Requirements

- User actions must create activity log and/or audit trail records where relevant.
- Batch close must produce summary and confidence context.

### 7.5 How-to-Use Requirements

- Guide page must present workflow in chronological shift order, not by feature list only.
- Each guide step must include objective, action, and completion signal.
- Guide language must remain role-friendly for operators with minimal product training.
- Guide content must stay aligned with active UI behavior and validation constraints.
- Guide page must be readable on mobile and tablet without hidden critical instructions.
- Guide page must include a compact field cheatsheet for in-shift reference.

## 8. Data Contract Requirements

### 8.1 Core Entity Set

- CircularInsight
- Anomaly
- ProductionBatch
- InventoryLog
- WasteLog
- Material
- ProductionTemplate (+ lines)
- ActivityLogEntry
- AuditTrailEntry
- BatchCloseSummary
- IntegrityOverview
- AnalyticsPayload
- ReportsPayload
- UserSettings

### 8.2 API Adapter Requirements

- Typed API interface shared across mock and spring providers
- Runtime provider selection via environment variable
- Configurable timeout for spring provider
- Optional fallback to mock provider in spring mode

Environment variables:
- VITE_INTERNAL_API_PROVIDER: mock | spring
- VITE_SPRING_API_BASE_URL
- VITE_SPRING_API_TIMEOUT_MS
- VITE_SPRING_FALLBACK_TO_MOCK

Recommendation:
- Production should set VITE_SPRING_FALLBACK_TO_MOCK=false to avoid silent fallback masking backend defects.

## 9. Backend API Contract (Frontend-Expected)

### 9.1 Endpoint List

- GET /api/v1/dashboard
- GET /api/v1/operations
- POST /api/v1/operations/batches
- POST /api/v1/operations/inventory-logs
- POST /api/v1/operations/waste-logs
- POST /api/v1/operations/waste-logs/recover
- GET /api/v1/operations/batch-close/summary/{batchId}
- POST /api/v1/operations/batch-close
- GET /api/v1/materials
- POST /api/v1/materials
- PUT /api/v1/materials/{id}
- GET /api/v1/templates
- POST /api/v1/templates
- PUT /api/v1/templates/{id}
- GET /api/v1/ai/insights
- PATCH /api/v1/ai/insights/{id}/status
- PATCH /api/v1/ai/anomaly/{id}/status
- GET /api/v1/analytics
- GET /api/v1/reports?period=weekly|monthly
- POST /api/v1/ai/ocr (multipart/form-data)
- GET /api/v1/settings
- PUT /api/v1/settings
- GET /api/v1/integrity/activity-logs
- GET /api/v1/integrity/audit-trail
- GET /api/v1/integrity/overview

### 9.2 Backend Validation Must Exist (Server-Side)

Frontend validation is only first-line UX protection.
Backend must enforce:
- Required fields and value bounds
- Referential integrity for batch/material/template relationships
- Waste recovery idempotency and status constraints
- Batch close threshold rule and reason requirement
- Authorization for write operations

## 10. Frontend Vs Backend Boundary

### 10.1 Frontend Responsibilities

- Present workflows and forms
- Client-side validation for fast feedback
- Route protection and UX session handling (current mock)
- Local state updates and interaction feedback
- Display formatting in local device time
- API error handling and retry affordances

### 10.2 Backend Responsibilities

- Authentication and authorization enforcement
- Persistent storage and transaction boundaries
- Domain validation as source of truth
- Circular score, confidence, and red-flag calculation integrity
- OCR provider integration and quality thresholds
- Recommendation/anomaly generation logic
- Auditability and compliance-level integrity records
- Notification policy and downstream delivery

### 10.3 Shared Contract Responsibilities

- Stable DTO schema and versioning strategy
- Consistent enums and status transitions
- Error payload structure
- Pagination/filtering for large datasets

## 11. Known Gaps Requiring Backend To Reach 100%

- Real auth (JWT/session/OAuth) and role-based access control
- Persistent database storage for all entities
- Concurrency handling for simultaneous operators
- Strong idempotency for recover and close operations
- Real OCR and AI model endpoints
- Alert delivery mechanisms (email/chat/webhook)
- Production-grade observability and audit retention

## 12. Non-Functional Requirements

### 12.1 Usability

- Must work on desktop and mobile breakpoints
- Critical actions must remain discoverable and reversible when possible
- Loading/error/empty states required for data surfaces

### 12.2 Performance

- Core route transitions should remain responsive
- Charts should render smoothly on mainstream hardware
- Avoid blocking user interaction on routine fetches

### 12.3 Reliability

- API errors must be surfaced clearly
- Data mutations must either complete or show actionable failure messages
- No silent data corruption paths in write workflows

### 12.4 Security

Current:
- Mock auth only, local session simulation

Target:
- JWT/OIDC auth
- Endpoint-level authorization
- Input validation and sanitization
- CORS policy and secure cookie/session settings where applicable

### 12.5 Observability

Target backend requirements:
- Request tracing with correlation IDs
- Structured audit logs for critical mutations
- Metrics for endpoint latency/error rates

## 13. Success Metrics

### 13.1 Product KPIs

- Weekly active workspace users
- Batch closure discipline rate
- Waste recovery rate and landfill share trend
- Recommendation application rate
- OCR-assisted intake adoption rate
- How-to-use completion rate for new users in first week
- Time to first successful full batch lifecycle for new operators

### 13.2 Technical KPIs

- Build success rate
- Frontend route error rate
- API request failure rate
- Time-to-interactive for key routes

## 14. Release Plan

### 14.1 Current Release (MVP)

- Full frontend workflow coverage for all workspace modules
- Mock-backed data and integrity simulation
- Spring API contract and runtime provider switch

### 14.2 Next Release (Backend Integration)

- Deploy Spring Boot + MySQL API with full endpoint parity
- Switch provider to spring in environment
- Disable mock fallback in production-like environments
- Run end-to-end integration validation against live API

### 14.3 Production Hardening

- Implement real auth and role policies
- Add backend migrations and CI checks
- Add observability dashboards and alerting
- Finalize data retention and compliance strategy

## 15. Acceptance Criteria Snapshot

- No frontend compile errors
- Tests pass for integrity-critical mock flows
- All protected routes reachable after auth
- Operations and OCR submissions blocked on invalid input
- Batch close requires confirmation and supports variance rules
- Dashboard and analytics render with data and graceful fallback states
- Guide page explains complete shift workflow with beginner-readable steps
- New user can follow guide and complete one end-to-end batch flow without external documentation

## 16. Implementation Reference

Primary implementation files include (current phase):
- src/App.tsx
- src/lib/api/internal-api.ts
- src/features/internal/mock-api.ts
- src/features/internal/types.ts
- src/pages/internal/DashboardPage.tsx
- src/pages/internal/OperationsPage.tsx
- src/pages/internal/ScanPage.tsx
- src/pages/internal/MaterialsPage.tsx
- src/pages/internal/TemplatesPage.tsx
- src/pages/internal/InsightsPage.tsx
- src/pages/internal/AnalyticsPage.tsx
- src/pages/internal/GuidePage.tsx
- src/pages/internal/SettingsPage.tsx
- src/components/ProtectedRoute.tsx
- src/lib/mock-auth.ts
