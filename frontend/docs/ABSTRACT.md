# WastePilot Abstract

WastePilot is a circular operations platform for manufacturing teams that want to reduce material waste and improve reuse outcomes.

The current product is a React and TypeScript web application with two experiences:
- A public landing experience that explains the value proposition and core capabilities.
- A protected internal workspace where teams track operations, scan invoices with OCR-assisted extraction, manage materials and production templates, review AI-style recommendations, analyze circularity trends, and configure workspace settings.

At its core, WastePilot combines three operating ideas:
- Visibility: central dashboards for circular score, waste trends, and efficiency.
- Actionability: guided recommendations and anomaly surfaces that can be marked and resolved.
- Operational control: day-to-day workflows for batch logging, inventory movement, waste disposition, and material management.

The latest product direction intentionally moves beyond a plain CRUD workspace:
- Mission Control on Dashboard prioritizes high-impact actions (integrity flags, anomaly response, recommendation adoption).
- Flow Assistant in Operations guides users through sequence-based execution (start batch -> log inventory -> classify waste -> close batch -> verify integrity).
- Action Queue in Insights surfaces estimated impact so teams execute outcomes, not just update record statuses.
- A dedicated How To Use page supports first-time onboarding with quick-start flow and field-level guidance.

The current implementation is frontend-first and uses a mock API layer with typed contracts, allowing rapid product iteration while preserving a clean migration path to a Spring Boot backend. Authentication is intentionally mocked for development, and all business entities are already modeled in TypeScript (materials, templates, batches, logs, insights, analytics, settings).

WastePilot is best understood as an MVP foundation for a production circular intelligence platform: the UI now emphasizes decision-centric operations, while production hardening (real auth, persistence, end-to-end testing, and live AI services) remains the next phase.
