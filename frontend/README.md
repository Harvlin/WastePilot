# WastePilot Frontend

WastePilot is a circular operations SaaS UI built with React + Vite + Tailwind and designed for Spring Boot API integration.

## Runtime Modes

The internal application uses an API abstraction layer at [src/lib/api/internal-api.ts](src/lib/api/internal-api.ts).

- `mock` mode: uses in-memory mocked services for all features/buttons/flows.
- `spring` mode: calls Spring Boot endpoints.

### Environment Variables

Set these in `.env`:

- `VITE_INTERNAL_API_PROVIDER=mock` or `spring`
- `VITE_SPRING_API_BASE_URL=http://localhost:8080`

Example:

```bash
VITE_INTERNAL_API_PROVIDER=mock
VITE_SPRING_API_BASE_URL=http://localhost:8080
```

## Spring Boot Endpoint Mapping

Configured in the API abstraction:

- `GET /api/v1/dashboard`
- `GET /api/v1/operations`
- `POST /api/v1/operations/batches`
- `POST /api/v1/operations/inventory-logs`
- `POST /api/v1/operations/waste-logs`
- `GET /api/v1/materials`
- `POST /api/v1/materials`
- `PUT /api/v1/materials/{id}`
- `GET /api/v1/templates`
- `POST /api/v1/templates`
- `PUT /api/v1/templates/{id}`
- `GET /api/v1/ai/insights`
- `PATCH /api/v1/ai/insights/{id}/status`
- `PATCH /api/v1/ai/anomaly/{id}/status`
- `GET /api/v1/analytics`
- `POST /api/v1/ai/ocr`
- `GET /api/v1/settings`
- `PUT /api/v1/settings`

## Scripts

- `npm run dev`
- `npm run build`
- `npm run test`
