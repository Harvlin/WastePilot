# API Conventions

Based on the review of `internal-api.ts` and the backend `com.project.wastepilot.controller` and `dto` packages, here is the summary of API conventions to follow for new features.

## Data Provider Pattern (Spring / Mock / Fallback)
The application uses a hybrid data fetching pattern defined in `internal-api.ts`.
- **Primary:** Spring Boot backend.
- **Fallback:** If a network error, timeout, or 500+ error occurs during a read operation, the `SpringBootInternalApi` automatically catches it and delegates the call to `MockInternalApi`.
- **Contracts:** Both the real and mock implementations implement the identical `InternalApi` interface.

## Naming Style
- **Endpoints:** RESTful, kebab-case paths prefixed with `/api/v1/`. E.g., `/api/v1/operations/batch-close`.
- **JSON Payload Properties:** camelCase (standard for Spring Boot Jackson and TypeScript).
- **TypeScript Types:** PascalCase interfaces/types, aligned exactly with backend DTOs.

## Response Envelope Shape
- **Success Responses:** The backend does **not** wrap successful responses in a generic `{"data": ...}` envelope. It returns the DTO object or JSON array directly.
  - Example: `GET /api/v1/materials` returns `[{ "id": "1", "name": "..." }]`.
  - Example: `POST /api/v1/auth/login` returns `{ "accessToken": "...", "user": {...} }`.
- **Empty Responses:** Return `204 No Content` or `200 OK` with a simple message object like `{ "message": "Success" }`.

## Error Format
All backend errors are intercepted by `GlobalExceptionHandler.java` and returned in a standard `ApiErrorResponse` shape:
```json
{
  "timestamp": "2026-08-01T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed.",
  "path": "/api/v1/auth/login"
}
```
The frontend `internal-api.ts` parses this, extracting the `message` property to throw an `ApiRequestError`.

## Pagination Pattern
Currently, endpoints like `GET /api/v1/materials` and `GET /api/v1/operations` do not use pagination (they return full arrays). However, if pagination is required for new features (e.g., Notification center), we will use Spring Data's standard page shape or simple query params `?page=0&size=20`, returning a structure like:
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```
*(Extrapolated convention for future pagination, as it aligns with standard Spring Boot).*

## Auth Header Pattern
- The frontend extracts the `accessToken` from `AuthSession` in local storage.
- It attaches it as a Bearer token: `Authorization: Bearer <token>` for all requests to the backend.

## DTO Conventions
- Request objects end in `Request` in the backend (e.g., `CreateBatchRequest`, `LoginRequest`).
- Response objects often end in `Response` in the backend (e.g., `BatchResponse`, `OperationsPayloadResponse`).
- In the frontend, the types are named after the domain entity (e.g., `ProductionBatch`, `InventoryLog`) or use `Payload`/`Input` suffixes (e.g., `CreateBatchInput`, `OperationsPayload`).
