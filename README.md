# WastePilot

[![CI](https://github.com/Harvlin/WastePilot/actions/workflows/ci.yml/badge.svg)](https://github.com/Harvlin/WastePilot/actions/workflows/ci.yml)

**Smart Circular Economy Platform for Manufacturing**

WastePilot is a production waste operational management platform built for small-to-mid-scale manufacturing plants. It combines a structured daily operational workflow — `start batch → log inventory → classify waste → close batch → integrity check` — with a **four-layer data integrity architecture** that treats human-entered data as something that can be manipulated, and defends against that reality by design rather than by assumption.

This is not a dashboard. It's a system built to be trusted with real operational decisions.

---

## Demo & Access

- **Live App:** https://waste-pilot.vercel.app/
- **Repository:** https://github.com/Harvlin/WastePilot
- **Walkthrough Video:** *(linked in submission — 3-minute end-to-end demo, kept as a fallback in case of live network issues)*

The backend is deployed publicly and connected by default. If you ever see a banner indicating fallback/mock mode, it means a transient backend read failure occurred — the app will recover automatically, and this is itself a deliberately engineered resilience feature, not a hidden limitation (see [Resilience by Design](#resilience-by-design)).

---

## Why WastePilot Is Different

Most waste-tracking tools fall into one of two categories: expensive enterprise MES modules (Siemens Opcenter, SAP, Plex) that assume you have an IT department, or EHS/compliance platforms (VelocityEHS, AMCS) priced for enterprises with regulatory obligations far beyond what a mid-size textile or garment plant needs. WastePilot sits deliberately in between — light enough to adopt in an afternoon, rigorous enough to be trusted with numbers that matter.

Three things set it apart:

1. **Data integrity is treated as an adversarial problem, not a formality.** WastePilot assumes operators *can* have incentives to misreport — to avoid filling out a justification form, to hide a bad shift — and is architected with four independent, complementary defenses against exactly that (see below). This is the single feature most competitors in this space don't have, at any price point.
2. **Honest AI, not AI-washing.** Every "smart" feature in this product is labeled precisely for what it is: a real Gemini Vision call for OCR, a real statistical z-score for anomaly detection, or an explicitly rule-based recommendation engine. Nothing is dressed up as AI-generated when it isn't.
3. **IoT-ready without requiring IoT.** The product works fully on manual entry from day one, but every ingestion path was designed so a physical sensor can plug into the same pipeline without architectural rework — proven with a working simulator, not just a roadmap promise.

---

## Data Integrity Architecture — The Core Differentiator

This is the part of WastePilot most worth understanding. Rather than relying on a single "trust the input" mechanism, the system layers four independent defenses:

| Layer | What It Does | When It Matters |
|---|---|---|
| **Audit Trail + Mandatory Reason** | Every post-hoc correction to recorded data requires a written justification (≥10 characters), enforced at the API level — not just the UI — and permanently logged with who, when, from-what, to-what | Forensic accountability, after the fact |
| **Anomaly Detection (Z-Score)** | Real-time statistical z-score computed against a 30-day rolling baseline per material/line, triggered automatically on every new waste log | Early detection, before a batch is even closed |
| **Threshold Gaming Detection** | Flags when an individual operator's batch closures cluster suspiciously often just under the mandatory-justification variance threshold — a pattern invisible to single-event anomaly checks | Detecting a sustained pattern of quiet threshold-dodging |
| **Cross-Validation** | Compares manually entered quantities against sensor-sourced quantities for the same batch/material, surfacing a discrepancy alert when they diverge beyond a reasonable tolerance | Prevention — once a sensor is connected, a human is no longer the sole source of truth |

Every signal from layers 2–4 is deliberately framed as **neutral information for a supervisor to review**, never an accusation — this was a conscious ethical decision, since statistical signals on small samples carry real false-positive risk. WastePilot surfaces what deserves a second look; it does not play judge.

---

## Key Features

### Authentication & Access Control
- JWT-based signup/login with access + refresh token pairs, independently configurable TTLs.
- BCrypt password hashing, auth-endpoint rate limiting, forgot/reset password flow.
- **Real RBAC**, enforced at the Spring Security layer via role claims embedded in the JWT — not a cosmetic label. `OPERATOR` and `SUPERVISOR` roles gate sensitive operations (batch close, red flag/anomaly resolution, pattern review access, post-close data corrections) at the authorization layer itself.
- Current user's name and role are always visible in the app shell.

### Operations (Core Workflow)
- Guided 5-step daily shift flow with active enforcement — required actions are disabled until prerequisites are met, not just validated after submission.
- Auto-generated batch close summary: variance, landfill share, and confidence score, with **every component computed from real aggregated data** — including `actualInputKg`, which sums real inventory-IN records rather than approximating them, and clearly flags itself as `measured` vs `estimated` depending on data availability.
- Post-close correction endpoint for supervisors, gated by RBAC and requiring a substantive reason — the audit trail's mandatory-reason requirement is backed by a real, testable code path, not just a passive scoring formula.

### AI Vision Scan
- Upload an invoice image, get material/quantity/unit extraction via a real Gemini Vision API call with JSON schema enforcement and proper error handling for auth/rate-limit/timeout cases — reviewed before it's committed to inventory.

### Sensor Ingestion (IoT Layer)
- `POST /api/v1/sensors/ingest` accepts readings from physical or simulated sensors, with sanity-bound validation to reject physically implausible values.
- A Python simulator (`tools/sensor-simulator/`) demonstrates the full pipeline without requiring physical hardware — proof that the architecture is genuinely ready for sensors, not just described as such.

### Integrity
- Activity logs, full field-level audit trail with before/after values, actor, timestamp, and reason.
- **Pattern Review**: statistical detection of threshold-gaming behavior per operator, visible to supervisors only.
- **Cross-Validation Alerts**: manual-vs-sensor discrepancy detection.
- Integrity Overview: average confidence, open red flags, overdue closures — all genuinely computed, no hardcoded placeholder values.

### Insights
- Rule-based recommendations (e.g. landfill share trending up across 3 consecutive batches, a material contributing disproportionately to waste) — explicitly labeled `(Rule-based recommendation)` in the response itself, never misrepresented as AI-generated.
- Action queue with status tracking and estimated impact per recommendation.

### Analytics & Reports
- Circularity trend, waste breakdown, material efficiency, landfill share/intensity — audited to ensure every figure is either genuinely computed or explicitly flagged as an estimate.
- Weekly/monthly period reports with auto-generated narrative highlights.
- **CSV/PDF export** for all period reports.
- Cross-line, cross-shift, cross-period comparison views.
- Sustainability impact estimation (CO2/landfill), with documented conversion-factor assumptions.

### Maturity Level Indicator
- Shows a plant's position on the adoption path (Level 1: manual → Level 2: partial sensors → Level 3: full sensor integration), with concrete next steps — the visual proof behind the "software-first, IoT-ready" claim.

---

## AI Strategy — What's Actually AI, and What Isn't

| Type | Feature | How It Actually Works |
|---|---|---|
| **Genuine AI (LLM)** | AI Vision Scan | Real Gemini Vision API call, JSON schema enforced |
| **Pure statistics** | Anomaly Detection | Z-score against real historical data, no model involved |
| **Rule-based** | Insights/Recommendations | Explicit programmed rules, labeled as such in every response |

This distinction is deliberate and stated up front, so it never has to be walked back under evaluator questioning. We'd rather be precise about three honest mechanisms than vague about one inflated claim.

---

## System Architecture

- later

### Frontend
- React 18 + TypeScript + Vite
- Routing: `react-router-dom`
- UI: Tailwind + Radix/shadcn components
- Centralized API client (`internal-api.ts`) with a flexible data-provider mode (`spring` / `mock` / `spring+fallback`)

### Backend
- Spring Boot 3.3.x (Java 21) — Spring Web, Security, Data JPA, Validation
- JWT Resource Server with a custom role-claim converter for RBAC enforcement
- Flyway migrations (8+ sequential, each documented with rationale for non-trivial data migrations)
- MySQL for runtime, H2 for the test profile
- Docker multi-stage build with a non-root runtime user

### Engineering Discipline Notes
- Consistent `BigDecimal` usage with `RoundingMode.HALF_UP` across all financial/quantity calculations — no floating-point drift anywhere in the money/weight math.
- Strict DTO/Entity separation via dedicated mappers, keeping API contracts stable independent of internal schema evolution.
- N+1 query awareness: aggregate endpoints like Pattern Review use a lightweight variance-only calculation path, separated from the full batch-close-summary generation, to avoid unnecessary query overhead at scale.
- The team ran an internal source-code audit mid-development and found (and fixed) hidden approximations that had been standing in for real calculations — including a formula-based estimate that was quietly substituting for real inventory aggregation, and a hardcoded confidence-score component. Both are documented and corrected. We consider this kind of self-audit a feature of our engineering process, not something to downplay.

---

## Resilience by Design

The frontend always attempts the real Spring Boot API first. If a specific read fails — a transient network blip, a cold-start delay — certain non-critical sections fall back to a clearly-labeled mock state rather than crashing the user's session. This means:

- The application never leaves an operator mid-shift with a broken screen.
- The fallback state is always visibly indicated, never silent — data provenance is never ambiguous to the person using the app.
- The demo remains stable under real-world network conditions, while the backend integration is genuinely live and load-bearing for the actual business logic (variance, confidence, integrity checks) that matters most.

---

## API Coverage

```
Auth
  POST   /api/v1/auth/signup
  POST   /api/v1/auth/login
  POST   /api/v1/auth/refresh
  GET    /api/v1/auth/me

Settings
  GET    /api/v1/settings
  PUT    /api/v1/settings

Materials
  GET    /api/v1/materials
  POST   /api/v1/materials
  PUT    /api/v1/materials/{id}
  DELETE /api/v1/materials/{id}

Templates
  GET    /api/v1/templates
  POST   /api/v1/templates
  PUT    /api/v1/templates/{id}
  DELETE /api/v1/templates/{id}

Operations
  GET    /api/v1/operations
  POST   /api/v1/operations/batches
  POST   /api/v1/operations/inventory-logs
  POST   /api/v1/operations/waste-logs
  POST   /api/v1/operations/waste-logs/recover
  GET    /api/v1/operations/batch-close/summary/{batchId}
  POST   /api/v1/operations/batch-close
  PATCH  /api/v1/operations/batches/{batchId}/output-units   [SUPERVISOR]

Sensors
  POST   /api/v1/sensors/ingest

Integrity
  GET    /api/v1/integrity/activity-logs
  GET    /api/v1/integrity/audit-trail
  GET    /api/v1/integrity/overview
  GET    /api/v1/integrity/pattern-review        [SUPERVISOR]
  GET    /api/v1/integrity/cross-validation      [SUPERVISOR]

Insights
  GET    /api/v1/insights
  PATCH  /api/v1/insights/{id}/status
  GET    /api/v1/anomalies
  PATCH  /api/v1/anomalies/{id}/status            [SUPERVISOR]

Analytics & Reports
  GET    /api/v1/analytics/*
  GET    /api/v1/reports/*
  GET    /api/v1/reports/export?format=csv|pdf
```

---

## Run Locally (Fullstack)

### Prerequisites
- Node.js 18+ (20+ recommended), npm
- Java 21
- Docker (recommended, for MySQL + backend in one step) — or a local MySQL instance

### 1) Backend (Docker, recommended)

```bash
cd wastepilot
cp .env.example .env
# Edit .env: set JWT_SECRET (openssl rand -base64 32) and GEMINI_API_KEY
docker compose up -d --build
```

Backend runs at `http://localhost:8088`. Adminer (DB viewer) at `http://localhost:8888`.

### 2) Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Frontend runs at `http://localhost:5173`.

### 3) Sensor Simulator (optional, for IoT demo)

```bash
cd tools/sensor-simulator
pip install -r requirements.txt
python simulate.py --config config.json
```

See `tools/sensor-simulator/README.md` for configuration details.

---

## Environment Configuration

### Frontend (`frontend/.env`)
| Variable | Purpose |
|---|---|
| `VITE_SPRING_API_PROVIDER` | `spring` or `mock` |
| `VITE_SPRING_API_BASE_URL` | Backend base URL |
| `VITE_SPRING_API_TIMEOUT_MS` | Request timeout before fallback consideration |
| `VITE_SPRING_FALLBACK_TO_MOCK` | `true`/`false` |

### Backend (`wastepilot/.env`)
| Variable | Purpose |
|---|---|
| `JWT_SECRET` | **Required.** No safe default — must be explicitly set in any non-local environment. |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` / `JWT_REFRESH_TOKEN_TTL_SECONDS` | Token lifetimes |
| `GEMINI_API_KEY` | **Required** for AI Vision Scan to function against the real API |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Database connection |
| `CORS_ALLOWED_ORIGIN_0` | Frontend origin allowed to call the API |

---

## Testing & Quality

```bash
# Backend — unit + integration tests
cd wastepilot
./mvnw test

# Backend — E2E-critical flows are additionally covered via integration tests
# spanning auth, RBAC, operations, sensor ingestion, pattern review, and
# cross-validation

# Frontend — unit tests
cd frontend
npm run test

# Frontend — E2E (Playwright)
npm run test:e2e

# Frontend — production build validation
npm run build
```

CI runs the full backend and frontend test suites on every push.

---

## Deployment

- **Frontend:** Vercel
- **Backend:** deployed via Docker to a managed platform (Railway/Render), with all secrets (`JWT_SECRET`, `GEMINI_API_KEY`, DB credentials) explicitly configured as environment variables — never relying on the local-dev fallback defaults present in `application.properties`, which exist solely to make local onboarding painless and are not safe for any public deployment.
- Health check available at `GET /actuator/health`.

---

## Roadmap Beyond the Competition

- Full multi-tenant SaaS architecture, building on the RBAC foundation already in place.
- Physical sensor pilot with a partner plant, moving beyond the simulator.
- Expanded cross-validation to cover additional data pairs beyond manual-vs-sensor (e.g. cross-operator consistency checks).
- Formal environmental compliance report formats for regional regulatory requirements.
- Deeper AI: moving select rule-based insights toward genuine model-assisted recommendations once sufficient real production data exists to train/validate against — deliberately not rushed ahead of having real data to justify it.

---

## Team

WastePilot Team

---
