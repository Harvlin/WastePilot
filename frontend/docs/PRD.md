# Product Requirements Document (PRD)

## 1. Product Overview

### 1.1 Product Name
WastePilot

### 1.2 Product Summary
WastePilot is a circular operations workspace for manufacturing teams. It helps operators and managers reduce waste, improve reuse rates, and make faster decisions through a unified interface for operations tracking, OCR-assisted intake, analytics, and AI-guided recommendations.

### 1.3 Product Stage
MVP / pre-production frontend with mocked data services and backend-ready API contracts.

## 2. Problem Statement

Manufacturing teams, especially small-to-mid sized operations, often manage waste and material flows through spreadsheets and fragmented tools. This creates delayed visibility, weak traceability, and poor optimization loops.

Even when teams adopt digital tools, many internal systems still behave like generic CRUD software, forcing users to manually decide next actions without impact context.

WastePilot addresses this by providing:
- A single workspace for circular operations.
- Structured waste and batch logging.
- Fast material intake through invoice image OCR.
- Continuous KPI visibility and anomaly awareness.
- Mission-driven guidance that prioritizes what to do next.

## 3. Goals and Non-Goals

### 3.1 Goals
- Centralize circular operations data in one product workflow.
- Reduce manual data entry effort for material intake.
- Provide actionable insight surfaces for operators and managers.
- Make weekly circular performance measurable and visible.
- Maintain a backend-agnostic frontend architecture that can switch from mock to Spring API.
- Shift core UX from entity CRUD to guided, impact-oriented execution.

### 3.2 Non-Goals (Current Scope)
- Full enterprise auth and identity management.
- Multi-tenant role-based authorization policy enforcement.
- Real ML model training or model orchestration.
- Multi-site orchestration and cross-site balancing.
- Financial accounting or ERP replacement.

## 4. Target Users and Personas

### 4.1 Operations Manager
- Owns throughput and waste reduction.
- Needs real-time awareness of batch and waste outcomes.
- Uses Dashboard, Operations, and Analytics daily.

### 4.2 Production Lead
- Owns line health and process reliability.
- Needs anomaly visibility and quick corrective actions.
- Uses Operations and Insights during shift cycles.

### 4.3 Sustainability or Circular Program Owner
- Owns reporting and long-term circular improvements.
- Needs trend analysis, score tracking, and recommendation adoption.
- Uses Dashboard, Insights, and Analytics weekly.

## 5. User Journeys

### 5.1 Material Intake Journey
1. User opens Scan.
2. User uploads or captures an invoice image.
3. System processes OCR and returns editable material lines.
4. User edits extracted rows and confirms save intent.

Success criteria:
- OCR rows are generated and editable.
- User can confirm and continue without runtime errors.

### 5.2 Batch Operations Journey
1. User opens Operations.
2. User follows Flow Assistant recommendations.
3. User creates a production batch.
4. User logs inventory movements and waste records.
5. User closes batch and reviews integrity output.

Success criteria:
- New logs appear immediately in lists.
- Waste totals and status summaries remain consistent.

### 5.3 Insight Action Journey
1. User opens Insights.
2. User reviews Action Queue with impact estimates.
3. User resolves top anomaly and/or applies recommendations.
4. Updated status persists in current session state.

Success criteria:
- Status actions are low friction.
- Updated cards reflect chosen state immediately.

### 5.4 Circular Performance Review Journey
1. User opens Dashboard and Analytics.
2. User checks Mission Control priorities on Dashboard.
3. User reviews circular score, trend charts, and period reports.
4. User executes linked actions from mission cards.

Success criteria:
- Charts load successfully.
- KPI context supports weekly planning decisions.

## 6. Functional Requirements

### 6.1 Routing and Access
- Public routes: landing, auth, not found.
- Protected routes: dashboard, scan, materials, templates, operations, insights, analytics, how-to-use, settings.
- Protected areas redirect unauthenticated users to auth page.

### 6.2 Authentication (Current)
- Mock sign-in via local storage.
- Mock sign-out clears local auth key.
- Social sign-in actions are simulation-only.

### 6.3 Dashboard
- Display circular score gauge.
- Display metric cards for input, waste, reuse.
- Display waste trend chart.
- Display anomaly highlight and insight preview.
- Display Mission Control queue with prioritized action cards and direct CTAs.

### 6.4 Operations
- Create production batch records.
- Add inventory IN and OUT logs.
- Add waste logs with destination and suggested action.
- Show aggregate summaries (running, completed, total waste).
- Provide Flow Assistant sequence with next-best-step navigation across tabs.

### 6.5 Scan (OCR)
- Accept image upload and camera capture input.
- Execute OCR process API call.
- Show editable OCR rows (name, quantity, unit, price).
- Confirm OCR rows for downstream logging readiness.

### 6.6 Materials
- List materials with category, grade, stock, supplier.
- Add and edit materials via modal form.
- Support circular grading values A, B, C.

### 6.7 Templates
- List production templates and line compositions.
- Add and edit template metadata and material lines.
- Add and remove line items in editor.

### 6.8 Insights
- Show recommendation cards and anomaly cards in separate tabs.
- Update item statuses with action buttons.
- Reflect status changes without full page reload.
- Provide Action Queue that combines high-priority recommendations and anomalies with estimated impact.
- Support bulk apply for new recommendations and quick resolve for top anomaly.

### 6.9 Analytics
- Show circularity trend line chart.
- Show waste breakdown pie chart.
- Show material efficiency bar chart.

### 6.10 Settings
- Edit profile metadata (company, email, role, timezone).
- Toggle anomaly and recommendation notifications.
- Edit daily AI token budget.

### 6.11 Search and Navigation
- Sidebar navigation to all internal modules.
- Header search maps keywords to workspace and landing destinations.
- Navigation order should support first-time learning flow.

### 6.12 How To Use Guide
- Provide dedicated onboarding page with quick-start modules and first-day checklist.
- Provide field cheatsheet for common operational inputs.
- Keep language concise and mobile-readable.

## 7. Data and API Requirements

### 7.1 Domain Entities
- Circular insight
- Anomaly
- Production batch
- Inventory log
- Waste log
- Material
- Production template
- OCR material line
- Dashboard payload
- Analytics payload
- User settings

### 7.2 API Layer Requirements
- Must provide typed internal API contract.
- Must support provider mode: mock or spring.
- Must support configurable timeout and fallback.
- Must support multipart request path for OCR image.

### 7.3 Spring Endpoint Targets
- GET /api/v1/dashboard
- GET /api/v1/operations
- POST /api/v1/operations/batches
- POST /api/v1/operations/inventory-logs
- POST /api/v1/operations/waste-logs
- GET and POST and PUT /api/v1/materials
- GET and POST and PUT /api/v1/templates
- GET and PATCH /api/v1/ai/insights
- GET and PATCH /api/v1/ai/anomaly
- GET /api/v1/analytics
- POST /api/v1/ai/ocr
- GET and PUT /api/v1/settings

## 8. Non-Functional Requirements

### 8.1 Usability
- Responsive behavior for mobile and desktop.
- Low-friction workflows for operators.
- Consistent component behavior and visual hierarchy.

### 8.2 Performance
- Initial route load should feel immediate in modern browsers.
- Chart-heavy views should remain interactive on common laptops.
- Simulated API delays should not block user actions unnecessarily.

### 8.3 Reliability
- Errors from API calls must show actionable retry states.
- Protected routing must be deterministic.

### 8.4 Security (Current and Target)
Current:
- Mock auth in local storage for development only.

Target:
- OAuth or JWT-backed auth.
- Secure session handling.
- Role-based authorization checks for sensitive operations.

### 8.5 Observability (Target)
- Frontend error tracking.
- API request diagnostics.
- Adoption analytics for key workflows.

## 9. Success Metrics

### 9.1 Product KPIs
- Weekly active operators in internal workspace.
- Percentage of production days with complete waste logs.
- Recommendation action rate (applied vs ignored).
- Circular score trend over rolling 4-week windows.
- OCR-assisted intake usage rate vs manual entry.

### 9.2 Reliability KPIs
- Internal route error rate.
- API request failure and fallback rate (spring mode).
- Median interactive load time per core page.

## 10. Release Scope

### 10.1 Current MVP Scope
- Public marketing and auth screens.
- Fully navigable internal workspace modules.
- Mock-backed mission-driven workspace behaviors (Mission Control, Flow Assistant, Action Queue).
- Configurable API adapter for spring integration.

### 10.2 Next Release Scope (Recommended)
- Real authentication and authorization.
- Backend persistence for all core entities.
- End-to-end tests for critical workflows.
- Real OCR provider integration.
- Notification pipeline for anomaly and recommendation events.

## 11. Risks and Mitigations

### Risk 1: Mock-production drift
Mitigation:
- Keep API contract parity tests between frontend adapter and backend endpoints.

### Risk 2: Low trust in insights
Mitigation:
- Add explainability metadata for each recommendation and anomaly source.

### Risk 3: Data quality issues from OCR
Mitigation:
- Keep editable review step mandatory before persistence.

### Risk 4: Under-tested release
Mitigation:
- Introduce baseline unit and e2e suites before production launch.

## 12. Open Questions

- Which auth provider and identity model will be used for production?
- What is the expected update cadence for dashboard and analytics data?
- Should recommendation status changes require audit trails?
- What minimum OCR confidence threshold is acceptable before operator review?
- Will materials and templates be shared across multiple sites in phase 2?

## 13. Implementation Reference (Current Code)

Primary implementation areas:
- src/App.tsx
- src/pages/Index.tsx
- src/pages/Auth.tsx
- src/pages/internal/DashboardPage.tsx
- src/pages/internal/OperationsPage.tsx
- src/pages/internal/ScanPage.tsx
- src/pages/internal/MaterialsPage.tsx
- src/pages/internal/TemplatesPage.tsx
- src/pages/internal/InsightsPage.tsx
- src/pages/internal/AnalyticsPage.tsx
- src/pages/internal/SettingsPage.tsx
- src/features/internal/mock-api.ts
- src/features/internal/types.ts
- src/lib/api/internal-api.ts
- src/lib/mock-auth.ts

---

Document status: Draft v1
Date: 2026-04-18
Owner: Product and Engineering
