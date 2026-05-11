# WastePilot

WastePilot is a production waste operational management application that helps factory teams run their daily workflows in a structured manner: starting from `start batch`, recording `inventory`, classifying `waste`, `batch close`, to `integrity` checks.

The main focus of this project is to create a system that is **ready for operational use**, not just a dashboard. Because of this, the architecture is built with a **backend-first** approach (Spring Boot API) and **selective frontend fallback** (mock) so that the UX continues to run when certain services are not yet available.

---

## Demo & Access

- **Link:** https://waste-pilot.vercel.app/
- **Repository:** https://github.com/Harvlin/WastePilot

> Important note for judges/users:  
> If the backend has not been deployed publicly, then when opening the public frontend link the application will run in hybrid/fallback mode according to the environment configuration.  
> To see the full backend integration, run the fullstack locally following the guide below.

---

## Why WastePilot is Interesting

- **Backend-first, not mock-first**: the frontend always tries the Spring API first, falling back to mock only for specific scenarios so the application remains usable.
- **Scalable clean architecture**: separation of `controller -> service -> repository`, clear DTO contracts, and mappers to maintain boundaries between layers.
- **Security-by-design**: JWT authentication, password hashing (`BCrypt`), auth endpoint rate limiting, login lockout, and controlled CORS.
- **Operational integrity as a core feature**: there are `Activity Logs`, `Audit Trail`, and `Integrity Overview` to maintain a trail of data changes.
- **Resilience for demo and growth**: features run even if AI components are not live yet, so the team can still show the end-to-end business process.
- **Real quality gates**: backend integration tests for critical flows (`auth`, `settings`, `materials`, `templates`, `operations`), plus frontend build validation.

---

## Key Features

### 1) Authentication
- JWT-based Signup/Login.
- `me` endpoint for session validation.
- Consistent error handling for invalid credentials, validation errors, etc.

### 2) Materials
- Production materials CRUD.
- Unique name, category, and circular grade validation.

### 3) Templates
- Production composition template CRUD.
- Sync line materials + unique SKU validation.

### 4) Operations (Core Workflow)
- Start batch.
- Log inventory IN/OUT.
- Log waste per batch + recovery to inventory.
- Auto summary for close batch (variance, landfill share, confidence).
- Close batch with reason validation when variance exceeds the threshold.

### 5) Integrity
- Activity logs.
- Audit trail of data changes.
- Integrity overview (confidence avg, open red flags, overdue closures).

### 6) Settings
- User profile/workspace settings.
- Backend contracts adapted to frontend needs.

### 7) Insights / Analytics / Reports
- The experience is already available on the frontend.
- For AI-heavy parts, it is currently designed to still run with mock/fallback.

---

## AI Strategy (Practical & Cost-Efficient)

WastePilot is prepared with an **AI-ready, production-safe** approach:

- **Current mode:** AI-dependent flows remain available via mock/fallback so operations are not blocked.
- **Planned provider:** `Google Gemini API` as the primary AI service because of fast integration and low cost (free tier available for the initial stage).
- **Phased AI implementation scope:**
  - `AI Vision Scan` for invoice/material line extraction.
  - `Insights` for waste reduction action recommendations and anomaly follow-ups.

This approach maintains a balance between **time-to-market**, **cost**, and **maintainability**.

---

## User Guide (Taken from the `/how-to-use` page)

This section reflects the actual workflow in the application.

## Core Shift Flow (Operations)

1. **Start Batch**
   - Fill in `Template Name`, `Output Units`, `Estimated Waste`.
   - Click `Start Batch`.
   - Done when the new batch appears with a `running` status.

2. **Log Material**
   - Go to `Inventory Input`.
   - Select the movement type `IN` or `OUT`.
   - For `OUT`, you must select a running batch.
   - Done when the log appears in the `Inventory Logs` table.

3. **Log Waste**
   - Select running batch, material, destination, quantity.
   - Use auto-convert for `reuse`/`repair` if needed.
   - Done when `Waste Logs` shows the destination + recovery status.

4. **Close Batch**
   - Open the `Batch Close Assistant`.
   - Review the auto summary.
   - If variance > threshold, fill in `Close Reason`.
   - Done when the batch status changes to `completed`.

5. **Integrity Check**
   - Open the integrity tab.
   - Review activity logs + audit trail.
   - Ensure no critical red flags are left behind.

## Supporting Modules

- **AI Vision Scan (`/scan`)**: OCR invoice -> row validation -> save as inventory IN.
- **Materials (`/materials`)**: manage master materials.
- **Production Templates (`/templates`)**: prepare recipe/baseline waste.
- **Insights (`/insights`)**: action queue of recommendations/anomalies.
- **Analytics (`/analytics`)**: trends and performance snapshots.

## Golden Rules

- Always ensure there is one running batch before logging `OUT` or waste.
- Fill in the quantity + unit correctly in every form.
- Fill in the close reason when the variance exceeds the threshold.

---

## System Architecture

### Frontend
- React 18 + TypeScript + Vite
- Routing: `react-router-dom`
- UI: Tailwind + Radix/shadcn components
- Centralized API client in `internal-api.ts`
- Data provider mode:
  - `spring` (active backend)
  - `mock` (frontend mock)
  - `spring + fallback` (hybrid)

### Backend
- Spring Boot 3.3.x (Java 21)
- Spring Web, Security, Data JPA, Validation
- JWT Resource Server
- Flyway migrations
- MySQL for runtime, H2 for test profile

### Data Flow
- Frontend submits requests to the backend API.
- Backend validation + business logic processing.
- Consistent error responses via global exception handler.
- For certain read operations, the frontend can fallback to mock if backend/network fails (according to configuration).

---

## API Coverage (currently available)

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

- `GET /api/v1/settings`
- `PUT /api/v1/settings`

- `GET /api/v1/materials`
- `POST /api/v1/materials`
- `PUT /api/v1/materials/{id}`
- `DELETE /api/v1/materials/{id}`

- `GET /api/v1/templates`
- `POST /api/v1/templates`
- `PUT /api/v1/templates/{id}`
- `DELETE /api/v1/templates/{id}`

- `GET /api/v1/operations`
- `POST /api/v1/operations/batches`
- `POST /api/v1/operations/inventory-logs`
- `POST /api/v1/operations/waste-logs`
- `POST /api/v1/operations/waste-logs/recover`
- `GET /api/v1/operations/batch-close/summary/{batchId}`
- `POST /api/v1/operations/batch-close`

- `GET /api/v1/integrity/activity-logs`
- `GET /api/v1/integrity/audit-trail`
- `GET /api/v1/integrity/overview`

---

## Run Locally (Fullstack)

## Prerequisites

- Node.js 18+ (20+ recommended)
- npm
- Java 21
- MySQL (default config available in `application.properties`)

## 1) Run Backend

```bash
cd wastepilot
./mvnw spring-boot:run
```

Backend defaults to `http://localhost:8080`.

## 2) Setup Frontend Env

Create `frontend/.env` from `frontend/.env.example`:

```env
VITE_INTERNAL_API_PROVIDER=spring
VITE_SPRING_API_BASE_URL=http://localhost:8080
VITE_SPRING_API_TIMEOUT_MS=10000
VITE_SPRING_FALLBACK_TO_MOCK=true
```

## 3) Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend defaults to `http://localhost:5173`.

---

## Environment Configuration

## Frontend (`frontend/.env`)

- `VITE_INTERNAL_API_PROVIDER` = `spring` or `mock`
- `VITE_SPRING_API_BASE_URL` = backend base URL
- `VITE_SPRING_API_TIMEOUT_MS` = request timeout to backend
- `VITE_SPRING_FALLBACK_TO_MOCK` = `true/false`

## Backend (`wastepilot/src/main/resources/application.properties`)

Key variables:
- `SERVER_PORT`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_ACCESS_TOKEN_TTL_SECONDS`

---

## Testing & Quality Check

## Backend

```bash
cd wastepilot
./mvnw -q test
```

## Frontend

```bash
cd frontend
npm run build
npm run test
```

---

## Judging / Verification Notes

- **If only opening the frontend deploy URL** and the backend is not published, the application will appear in fallback/mock mode according to the env.
- **If you want to verify the real backend**, judges are advised to run the fullstack locally.
- This approach was chosen so the demo remains stable, while also showing a real backend implementation that is ready for further development.

---

## Roadmap

- Deploy public backend for full online E2E demo.
- Hardening audit/recovery relations so all metadata is fully persistent.
- Integration of production AI service (OCR/insight engine) replacing placeholders/mocks.
- Adding observability (structured logs, metrics dashboards).

---

## Team

WastePilot Team

---