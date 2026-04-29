# Backend Implementation Guide (Spring Boot + Maven + MySQL)

## 1. Purpose

This guide translates the current WastePilot frontend contracts into a production-ready backend blueprint.

Target stack:
- Java 21
- Spring Boot 3.x
- Maven
- MySQL 8
- Spring Security (JWT/OAuth2 resource server)
- Spring Data JPA
- MapStruct
- Docker + Docker Compose

Use this document as the default execution plan for backend engineering.

## 2. Frontend Contract Alignment

The frontend already expects these base endpoints:

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
- POST /api/v1/auth/login
- POST /api/v1/auth/register
- POST /api/v1/auth/forgot-password

Important contract notes:
- Response JSON must match frontend type fields and casing.
- Enum values must stay stable (e.g., running/completed, reuse/repair/dispose, new/applied/ignored).
- Server-side validation is mandatory even when frontend validates.

## 3. Recommended Project Structure

```text
wastepilot-backend/
  pom.xml
  Dockerfile
  docker-compose.yml
  src/main/java/com/wastepilot/
    WastePilotApplication.java
    config/
      SecurityConfig.java
      CorsConfig.java
      JacksonConfig.java
    common/
      exception/
      response/
      util/
    auth/
      JwtAuthenticationFilter.java
      CurrentUser.java
    domain/
      material/
      template/
      operation/
      insight/
      analytics/
      integrity/
      settings/
      ai/
    infra/
      persistence/
      ai/
      logging/
  src/main/resources/
    application.properties
    application-docker.properties
  src/test/java/
```

## 4. Maven Configuration

Maven setup is implemented in [wastepilot/pom.xml](wastepilot/pom.xml).
Keep dependency changes there to avoid duplication in this guide.

## 5. Configuration (application properties)

Configuration is implemented in:
- [wastepilot/src/main/resources/application.properties](wastepilot/src/main/resources/application.properties)
- [wastepilot/src/main/resources/application-docker.properties](wastepilot/src/main/resources/application-docker.properties)

Use UTC as canonical timezone and rely on env vars for DB and runtime settings.

DDL guidance:
- Local dev only: ddl-auto=update is acceptable for speed.
- Shared/prod environments: use ddl-auto=validate and manage schema changes manually.

## 6. Core Domain Entities

✅ Implemented in `com.project.wastepilot.domain`

## 7. DTO Design

✅ Implemented in `com.project.wastepilot.dto`

## 8. MapStruct Mapping

Use MapStruct to keep controller/service clean.

```java
@Mapper(componentModel = "spring")
public interface MaterialMapper {
  MaterialResponse toResponse(MaterialEntity entity);
  List<MaterialResponse> toResponseList(List<MaterialEntity> entities);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void update(@MappingTarget MaterialEntity target, UpsertMaterialRequest source);
}
```

MapStruct best practice:
- Keep transformation-only logic in mappers.
- Keep business rules in services.

## 9. Repository Layer

✅ Implemented in `com.project.wastepilot.repository`

## 10. Service Layer (Business Rules)

Implement business rules here, with @Transactional where needed.

### 10.1 Batch create

Responsibilities:
- Validate template exists
- Create running batch
- Optionally decrement planned material stock based on template lines
- Write activity log

### 10.2 Inventory log create

Responsibilities:
- Validate material fields
- Require batch for OUT logs
- Reject invalid batch states
- Write activity log

### 10.3 Waste log create

Responsibilities:
- Validate running batch
- Persist waste log with destination
- Set recovery status (pending/not-applicable)
- If post-close edit: write audit and red flag

### 10.4 Waste recover to inventory

Responsibilities:
- Ensure waste exists
- Reject destination=dispose
- Reject already converted
- Create inventory IN recovery log
- Update waste status to converted
- Write activity + audit logs

All in one transaction:

```java
@Transactional
public WasteRecoveryResult recoverToInventory(UUID wasteLogId, String actor) {
  // lock row, validate state, write both records, commit atomically
}
```

### 10.5 Batch close

Responsibilities:
- Ensure batch is running
- Compute summary and variance
- Enforce variance threshold reason rule
- Finalize batch, score snapshot, confidence, red flags
- Write activity + optional audit

Threshold should come from config (not hardcoded).

## 11. Controller Design

Suggested controllers:
- DashboardController
- OperationsController
- MaterialsController
- TemplatesController
- InsightsController
- AnalyticsController
- SettingsController
- IntegrityController
- AiController

Example endpoint:

```java
@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationsController {
  private final OperationsService service;

  @PostMapping("/batch-close")
  public BatchCloseSummaryResponse closeBatch(@Valid @RequestBody CloseBatchRequest request,
                                              @AuthenticationPrincipal Jwt principal) {
    return service.closeBatch(request, principal.getSubject());
  }
}
```

## 12. Security Implementation

Minimum production baseline:
- Stateless JWT bearer authentication
- Single access level (authenticated users only)
- CORS allow frontend origin only
- CSRF disabled for stateless API

Example security config:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
  http
    .csrf(csrf -> csrf.disable())
    .cors(Customizer.withDefaults())
    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
      .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
      .requestMatchers("/api/v1/**").authenticated()
      .anyRequest().authenticated()
    )
    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

  return http.build();
}
```

## 13. AI Integration Plan

For full implementation details and project-init snippets, see Section 28 (AI Implementation Pack).

### 13.1 OCR endpoint

POST /api/v1/ai/ocr
- Input: multipart image file
- Output: list of OCR material lines (materialName, quantity, unit, price)

Provider adapter pattern:
- OcrProvider interface
- GeminiOcrProvider implementation
- MockOcrProvider fallback for non-prod

### 13.2 Recommendation + anomaly generation

Options:
- synchronous inference endpoint
- scheduled job pipeline (recommended for production)

Recommended design:
- daily/shift scheduler computes new recommendations and anomalies
- persist results to tables
- GET /api/v1/ai/insights reads from persistence

### 13.3 Token budget integration

Use UserSettings.dailyTokenBudget to enforce per-day provider call limits.

Backend must:
- track token usage per workspace/user/day
- reject or degrade gracefully when limit exceeded

## 14. Dockerization

Docker setup is implemented in:
- [wastepilot/Dockerfile](wastepilot/Dockerfile)
- [wastepilot/docker-compose.yml](wastepilot/docker-compose.yml)
- [wastepilot/.env](wastepilot/.env)

## 15. Schema Management (No Flyway)

Use JPA `ddl-auto` for local development only, and manage schema changes manually for shared/prod.
Recommended rules:
- Keep all timestamps in UTC.
- Add indexes for batchId, timestamp, and status.
- Add unique constraints where the domain requires (e.g., material name).

## 16. Error Handling Contract

Return consistent error shape, for example:

```json
{
  "timestamp": "2026-04-19T16:20:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Variance above threshold requires close reason.",
  "path": "/api/v1/operations/batch-close"
}
```

Frontend expectation:
- show message in toast
- keep user on same screen for correction

## 17. Logging And Observability

Implement:
- structured JSON logs
- request correlation ID (MDC)
- actuator health/metrics
- slow query logging for MySQL

Track these metrics:
- API latency per endpoint
- 4xx/5xx rates
- OCR success/failure rate
- recommendation generation duration
- batch close failure rate

## 18. Testing Strategy

### 18.1 Unit tests

- service rule validation
- score and confidence calculation
- mapper correctness

### 18.2 Integration tests

- repository + MySQL Testcontainers
- full controller tests for critical endpoints

Critical path integration tests:
- create batch -> log inventory -> log waste -> close batch
- waste recover conversion and idempotency
- status patch for recommendations/anomalies

### 18.3 Contract tests

- validate response payload fields against frontend expectations
- ensure enum values are stable

## 19. Feature Delivery Roadmap

Phase 1 (foundation):
- project skeleton, security baseline
- materials/templates/settings CRUD

Phase 2 (operations core):
- operations read model
- batch create, inventory log, waste log, recover, close summary/close
- activity/audit logging

Phase 3 (insights and analytics):
- insights and anomaly persistence + status updates
- dashboard, analytics, reports endpoints

Phase 4 (AI integration):
- OCR provider integration
- recommendation/anomaly generation pipeline
- token budget enforcement

Phase 5 (hardening):
- observability, resilience, load/security tests
- production rollout checklist

## 20. Frontend-Backend Handshake Checklist

- Endpoint paths and methods match exactly.
- DTO field names match frontend types.
- Enum values match frontend expectations.
- Timestamps are ISO-8601 UTC.
- Validation and business-rule errors return readable messages.
- CORS allows frontend origin(s).
- Auth strategy agreed and documented.
- Mock fallback disabled in production frontend config.

## 21. Backend-Owned Items Required For Full Completion

These cannot be fully solved in frontend-only scope:
- real identity and authorization policy
- persistent audit integrity with immutable logs
- transactional consistency across multi-entity writes
- robust AI/OCR reliability and retries
- environment-level secrets and key management
- production-grade data retention and backup

## 22. Final Notes

- Keep backend source of truth for all business rules.
- Keep frontend as UX-first validation and orchestration layer.
- Preserve contract stability during rollout to avoid breaking existing frontend workflows.

## 23. Project-Init Code Pack (Entities + DTOs + Repositories)

✅ All boilerplate snippets have been successfully implemented and removed from this guide to keep it clean.

## 26. Service Layer: How To Implement (practical blueprint)

### 26.1 Recommended service package layout

```text
domain/
  operation/
    OperationsService.java
    OperationsServiceImpl.java
    OperationValidator.java
  material/
    MaterialService.java
    MaterialServiceImpl.java
  template/
    TemplateService.java
    TemplateServiceImpl.java
  insight/
    InsightService.java
    InsightServiceImpl.java
  analytics/
    AnalyticsService.java
    AnalyticsServiceImpl.java
  settings/
    SettingsService.java
    SettingsServiceImpl.java
  integrity/
    IntegrityService.java
    IntegrityServiceImpl.java
```

### 26.2 Service method pattern (repeat this in all domains)

Use this sequence for every write use case:
1. Validate request DTO constraints and semantic rules.
2. Load required aggregates from repositories.
3. Apply business rules (status transitions, thresholds, idempotency).
4. Persist primary entity changes.
5. Persist side effects (activity log, audit trail, red flags).
6. Map entities to response DTOs.
7. Return response.

### 26.3 Transaction boundary matrix

- `@Transactional(readOnly = true)` for read-only endpoints.
- `@Transactional` required for:
  - create batch
  - create inventory log
  - create waste log
  - recover waste to inventory (must be atomic)
  - close batch
  - status patch endpoints

### 26.4 OperationsService interface starter

```java
public interface OperationsService {
  OperationsPayloadResponse getOperationsPayload();
  BatchResponse createBatch(CreateBatchRequest request, String actor);
  InventoryLogResponse createInventoryLog(CreateInventoryLogRequest request, String actor);
  WasteLogResponse createWasteLog(CreateWasteLogRequest request, String actor);
  WasteRecoveryResponse recoverWasteToInventory(RecoverWasteRequest request, String actor);
  BatchCloseSummaryResponse getBatchCloseSummary(String batchId);
  BatchCloseSummaryResponse closeBatch(CloseBatchRequest request, String actor);
}
```

### 26.4.1 Input normalization + validation helper (recommended)

Keep semantic validation in services while normalizing input early:

```java
private static String requireTrim(String value, String field) {
  if (value == null || value.trim().isEmpty()) {
    throw new BusinessException(field + " is required.");
  }
  return value.trim();
}

private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
  try {
    return Enum.valueOf(type, value.trim());
  } catch (Exception ex) {
    throw new BusinessException("Invalid " + field + ": " + value);
  }
}
```

Use these helpers in every service method before persisting.

### 26.4.2 Activity + audit writer helper

Centralize audit writes to avoid duplicate logic:

```java
@Component
@RequiredArgsConstructor
public class AuditWriter {
  private final ActivityLogRepository activityLogRepository;
  private final AuditTrailRepository auditTrailRepository;

  public void activity(EntityType entity, String entityId, String action, String actor, String detail) {
    ActivityLogEntity log = new ActivityLogEntity();
    log.setEntity(entity);
    log.setEntityId(entityId);
    log.setAction(action);
    log.setActor(actor);
    log.setDetail(detail);
    log.setTimestamp(Instant.now());
    activityLogRepository.save(log);
  }

  public void audit(EntityType entity, String entityId, String field,
                    String oldValue, String newValue, String actor) {
    AuditTrailEntity audit = new AuditTrailEntity();
    audit.setEntity(entity);
    audit.setEntityId(entityId);
    audit.setField(field);
    audit.setOldValue(oldValue);
    audit.setNewValue(newValue);
    audit.setActor(actor);
    audit.setTimestamp(Instant.now());
    auditTrailRepository.save(audit);
  }
}
```

### 26.4.3 Operations payload read-model assembly

Keep the list endpoints fast and consistent by aggregating once:

```java
@Transactional(readOnly = true)
public OperationsPayloadResponse getOperationsPayload() {
  List<BatchResponse> batches = batchRepository.findByStatusOrderByStartedAtDesc(BatchStatus.running)
    .stream().map(batchMapper::toResponse).toList();

  List<InventoryLogResponse> inventoryLogs = inventoryLogRepository.findTop200ByOrderByTimestampDesc()
    .stream().map(inventoryMapper::toResponse).toList();

  List<WasteLogResponse> wasteLogs = wasteLogRepository.findTop200ByOrderByTimestampDesc()
    .stream().map(wasteMapper::toResponse).toList();

  return new OperationsPayloadResponse(batches, inventoryLogs, wasteLogs);
}
```

### 26.4.4 Status patch pattern (insight/anomaly)

Patch endpoints should be idempotent and validate allowed status values:

```java
@Transactional
public CircularInsightResponse updateInsightStatus(UUID id, StatusPatchRequest request, String actor) {
  InsightStatus status = parseEnum(InsightStatus.class, request.status(), "status");
  InsightEntity entity = insightRepository.findById(id)
    .orElseThrow(() -> new NotFoundException("Insight not found."));

  String oldStatus = entity.getStatus().name();
  entity.setStatus(status);
  insightRepository.save(entity);

  auditWriter.activity(EntityType.insight, entity.getId().toString(), "status_patch",
    actor, "Status " + oldStatus + " -> " + status.name());

  return insightMapper.toResponse(entity);
}
```

### 26.5 Example: inventory log service implementation

```java
@Service
@RequiredArgsConstructor
public class OperationsServiceImpl implements OperationsService {

  private final BatchRepository batchRepository;
  private final InventoryLogRepository inventoryLogRepository;
  private final ActivityLogRepository activityLogRepository;

  @Override
  @Transactional
  public InventoryLogResponse createInventoryLog(CreateInventoryLogRequest request, String actor) {
    InventoryType type = InventoryType.valueOf(request.type());

    BatchEntity batch = null;
    if (type == InventoryType.OUT) {
      if (request.batchId() == null || request.batchId().isBlank()) {
        throw new BusinessException("OUT log requires running batch.");
      }

      UUID batchId = UUID.fromString(request.batchId());
      batch = batchRepository.findById(batchId)
        .orElseThrow(() -> new NotFoundException("Batch not found."));

      if (batch.getStatus() != BatchStatus.running) {
        throw new BusinessException("Selected batch is not running.");
      }
    }

    InventoryLogEntity log = new InventoryLogEntity();
    log.setBatch(batch);
    log.setMaterialName(request.materialName().trim());
    log.setType(type);
    log.setQuantity(request.quantity());
    log.setUnit(request.unit().trim());
    log.setSource(request.source().trim());
    log.setTimestamp(Instant.now());
    inventoryLogRepository.save(log);

    ActivityLogEntity activity = new ActivityLogEntity();
    activity.setEntity(EntityType.inventory);
    activity.setEntityId(log.getId().toString());
    activity.setAction("created");
    activity.setActor(actor);
    activity.setDetail("Inventory log created");
    activity.setTimestamp(Instant.now());
    activityLogRepository.save(activity);

    return new InventoryLogResponse(
      log.getId().toString(),
      log.getBatch() == null ? null : log.getBatch().getId().toString(),
      log.getMaterialName(),
      log.getType().name(),
      log.getQuantity(),
      log.getUnit(),
      log.getSource(),
      log.getTimestamp()
    );
  }
}
```

### 26.6 Example: recover waste to inventory (idempotent + atomic)

```java
@Override
@Transactional
public WasteRecoveryResponse recoverWasteToInventory(RecoverWasteRequest request, String actor) {
  UUID wasteId = UUID.fromString(request.wasteLogId());
  WasteLogEntity waste = wasteLogRepository.lockById(wasteId)
    .orElseThrow(() -> new NotFoundException("Waste log not found."));

  if (waste.getDestination() == WasteDestination.dispose) {
    throw new BusinessException("Dispose waste cannot be recovered.");
  }

  if (waste.getRecoveryStatus() == RecoveryStatus.converted) {
    throw new BusinessException("Waste already converted.");
  }

  InventoryLogEntity recovered = new InventoryLogEntity();
  recovered.setBatch(waste.getBatch());
  recovered.setMaterialName(waste.getMaterialName());
  recovered.setType(InventoryType.IN);
  recovered.setQuantity(waste.getQuantityKg());
  recovered.setUnit("kg");
  recovered.setSource("RECOVERY");
  recovered.setTimestamp(Instant.now());
  inventoryLogRepository.save(recovered);

  waste.setRecoveryStatus(RecoveryStatus.converted);
  wasteLogRepository.save(waste);

  // write activity + optional audit
  // map to WasteRecoveryResponse
}
```

### 26.7 Example: close batch rule enforcement

```java
@Override
@Transactional
public BatchCloseSummaryResponse closeBatch(CloseBatchRequest request, String actor) {
  UUID batchId = UUID.fromString(request.batchId());
  BatchEntity batch = batchRepository.lockById(batchId)
    .orElseThrow(() -> new NotFoundException("Batch not found."));

  if (batch.getStatus() != BatchStatus.running) {
    throw new BusinessException("Batch is not running.");
  }

  BigDecimal variance = request.outputUnits().subtract(batch.getOutputUnits()).abs();
  BigDecimal threshold = BigDecimal.valueOf(5); // inject from config in real implementation

  if (variance.compareTo(threshold) > 0 && (request.closeReason() == null || request.closeReason().isBlank())) {
    throw new BusinessException("Variance above threshold requires close reason.");
  }

  batch.setOutputUnits(request.outputUnits());
  batch.setStatus(BatchStatus.completed);
  batch.setClosedAt(Instant.now());
  batch.setClosedBy(actor);
  batch.setCloseReason(request.closeReason());
  batchRepository.save(batch);

  // write activity, audit (if needed), red-flag resolution checks
  // return summary DTO used by frontend
}
```

### 26.8 Service layer execution checklist (what to implement first)

Phase A (must-have to run core frontend):
1. MaterialsService: list + upsert.
2. TemplatesService: list + upsert.
3. OperationsService: payload, create batch, create inventory, create waste, recover, close summary, close batch.
4. IntegrityService: activity logs, audit trail, integrity overview.
5. SettingsService: get + update.

Phase B (decision support):
1. InsightsService: get insights + patch statuses.
2. DashboardService: compose mission control payload.
3. AnalyticsService: analytics + reports payload.

Phase C (AI integration):
1. AiService OCR endpoint.
2. Recommendation/anomaly generation job.
3. Daily token budget enforcement.

### 26.9 Common service-layer mistakes to avoid

1. Putting business rules in controllers instead of services.
2. Missing transaction on multi-write operations.
3. Returning entities directly to API (leaks persistence model).
4. Hardcoding enums/status strings not aligned with frontend.
5. Skipping lock/idempotency in recover/close paths.
6. Writing activity log outside transaction and losing consistency.

## 27. Security Implementation Pack (Project-Init Ready)

This section extends Section 12 with practical security snippets you can implement directly.

### 27.1 Security architecture decision

Recommended default:
- Resource server mode with external IdP (Keycloak, Auth0, Azure AD, etc).
- Backend validates bearer JWT.
- Authorization is enforced by authentication only (all /api/v1/** require a valid JWT).

Optional fallback for early internal testing:
- Local username/password auth module with issued JWT.
- Keep this behind non-production profile.

### 27.2 Security properties (application.yml)

```yaml
wastepilot:
  security:
    cors-allowed-origins:
      - http://localhost:5173
    max-upload-size-mb: 10
    allowed-upload-content-types:
      - image/png
      - image/jpeg
      - image/webp
    rate-limit:
      enabled: true
      requests-per-minute: 120
```

### 27.3 Typed properties class

```java
@ConfigurationProperties(prefix = "wastepilot.security")
@Validated
public record SecurityProps(
  @NotEmpty List<String> corsAllowedOrigins,
  @Min(1) @Max(50) int maxUploadSizeMb,
  @NotEmpty List<String> allowedUploadContentTypes,
  RateLimit rateLimit
) {
  public record RateLimit(boolean enabled, @Min(1) int requestsPerMinute) {}
}
```

Register properties:

```java
@Configuration
@EnableConfigurationProperties(SecurityProps.class)
public class SecurityPropertiesConfig {}
```

### 27.4 CORS configuration snippet

```java
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

  private final SecurityProps securityProps;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(securityProps.corsAllowedOrigins());
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
    cfg.setExposedHeaders(List.of("X-Correlation-Id"));
    cfg.setAllowCredentials(false);
    cfg.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
```

### 27.5 Security config with JSON error handlers

```java
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final AuthenticationEntryPoint authEntryPoint;
  private final AccessDeniedHandler accessDeniedHandler;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .cors(Customizer.withDefaults())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .headers(h -> h
        .contentTypeOptions(Customizer.withDefaults())
        .frameOptions(f -> f.deny())
        .xssProtection(Customizer.withDefaults())
        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
      )
      .exceptionHandling(e -> e
        .authenticationEntryPoint(authEntryPoint)
        .accessDeniedHandler(accessDeniedHandler)
      )
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
        .requestMatchers("/api/v1/**").authenticated()
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

    return http.build();
  }
}
```

### 27.6 JSON auth error responses (401 and 403)

```java
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), Map.of(
      "timestamp", Instant.now().toString(),
      "status", 401,
      "code", "UNAUTHORIZED",
      "message", "Authentication required.",
      "path", request.getRequestURI()
    ));
  }
}

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), Map.of(
      "timestamp", Instant.now().toString(),
      "status", 403,
      "code", "FORBIDDEN",
      "message", "You are not allowed to perform this action.",
      "path", request.getRequestURI()
    ));
  }
}
```

### 27.7 Ownership checks pattern (no roles)

Use explicit checks in the service layer for sensitive updates.

```java
private void ensureCanModifyBatch(BatchEntity batch, String actor) {
  if (batch.getClosedBy() != null && !batch.getClosedBy().equals(actor)) {
    throw new AccessDeniedException("Batch is owned by another operator.");
  }
}
```

### 27.8 OCR upload hardening

Apply strict guards before sending files to OCR provider.

```java
public void validateOcrUpload(MultipartFile file, SecurityProps props) {
  if (file == null || file.isEmpty()) {
    throw new BusinessException("Image file is required.");
  }

  String contentType = Optional.ofNullable(file.getContentType()).orElse("");
  if (!props.allowedUploadContentTypes().contains(contentType)) {
    throw new BusinessException("Unsupported file content type: " + contentType);
  }

  long maxBytes = (long) props.maxUploadSizeMb() * 1024 * 1024;
  if (file.getSize() > maxBytes) {
    throw new BusinessException("File exceeds max upload size.");
  }
}
```

Hardening recommendations:
- Strip file name/path.
- Reject SVG for OCR upload unless sanitized.
- Add malware scanning hook for production.

### 27.9 Rate limiting filter (optional but recommended)

```java
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
  private final SecurityProps securityProps;
  private final ConcurrentMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
  private volatile long windowStart = System.currentTimeMillis();

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!securityProps.rateLimit().enabled()) {
      chain.doFilter(request, response);
      return;
    }

    long now = System.currentTimeMillis();
    if (now - windowStart > 60_000) {
      counters.clear();
      windowStart = now;
    }

    String key = Optional.ofNullable(request.getRemoteAddr()).orElse("unknown");
    int count = counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();

    if (count > securityProps.rateLimit().requestsPerMinute()) {
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
      return;
    }

    chain.doFilter(request, response);
  }
}
```

Register filter before authentication if desired.

### 27.10 Security event auditing

Log these events to activity/audit streams:
- authentication failures (without sensitive token dump)
- forbidden access attempts
- status changes on insight/anomaly
- batch close with high variance
- waste recovery conversion

Example helper:

```java
public void writeSecurityAudit(String actor, String action, String detail) {
  ActivityLogEntity log = new ActivityLogEntity();
  log.setEntity(EntityType.settings);
  log.setEntityId("security");
  log.setAction(action);
  log.setActor(actor);
  log.setDetail(detail);
  log.setTimestamp(Instant.now());
  activityLogRepository.save(log);
}
```

### 27.11 Secrets and key management

Production rules:
- Never commit JWT issuer URIs, client secrets, API keys, DB passwords to git.
- Inject via environment variables or secret manager (Vault, AWS Secrets Manager, Azure Key Vault).
- Rotate credentials regularly.
- Use separate secrets per environment.

Suggested env variables:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI
OCR_PROVIDER_API_KEY
```

### 27.12 Security testing checklist

1. Unauthorized request returns 401 JSON shape.
2. Authenticated but insufficient role returns 403 JSON shape.
3. CORS only allows configured frontend origin.
4. OCR upload rejects invalid content-type and oversized files.
5. Rate limit returns 429 after threshold.
6. Recover endpoint remains idempotent under concurrent requests.
7. Close batch high-variance rule cannot be bypassed.
8. No stack trace leaks in API error responses.

### 27.13 Optional local-auth bootstrap (non-production profile)

If external IdP is not ready, you can temporarily add:
- POST /api/v1/auth/login
- POST /api/v1/auth/refresh
- BCrypt password hash storage

Keep this behind a dedicated profile and remove or disable in production.

## 28. AI Implementation Pack (Project-Init Ready)

This section expands Section 13 into an implementation-ready AI blueprint.

Goals:
- OCR pipeline that is reliable and safe for production traffic.
- Recommendation and anomaly generation with deterministic persistence.
- Token budget enforcement with graceful degradation.
- Stable API outputs for frontend contracts.

### 28.1 AI architecture overview

Recommended components:
- `AiController`: HTTP entry points (`/api/v1/ai/ocr`, optional admin recompute endpoints).
- `OcrService`: input validation, provider call, output normalization.
- `RecommendationService`: generate and persist recommendations.
- `AnomalyService`: detect anomalies from operational history.
- `TokenBudgetService`: enforce per-user/day limits.
- `AiUsageAuditService`: record provider usage and failures.
- `AiScheduler`: shift/daily job orchestration.

Execution model:
- OCR is request/response (synchronous).
- Recommendations/anomalies are batch jobs (scheduled, persisted).
- Frontend reads persisted records through existing endpoints.

### 28.2 AI configuration (application.yml)

```yaml
wastepilot:
  ai:
    provider: gemini
    request-timeout-ms: 15000
    max-retries: 2
    ocr:
      enabled: true
      confidence-threshold: 0.55
      max-image-size-mb: 10
    insights:
      enabled: true
      schedule-cron: "0 0 */4 * * *" # every 4 hours
      max-recommendations-per-run: 30
      max-anomalies-per-run: 30
    token-budget:
      enabled: true
      default-daily-budget: 100000
      hard-fail-on-exceed: false
```

### 28.3 Typed AI properties

```java
@ConfigurationProperties(prefix = "wastepilot.ai")
@Validated
public record AiProps(
  @NotBlank String provider,
  @Min(1000) int requestTimeoutMs,
  @Min(0) @Max(5) int maxRetries,
  Ocr ocr,
  Insights insights,
  TokenBudget tokenBudget
) {
  public record Ocr(boolean enabled, @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidenceThreshold,
                    @Min(1) @Max(50) int maxImageSizeMb) {}

  public record Insights(boolean enabled, @NotBlank String scheduleCron,
                         @Min(1) int maxRecommendationsPerRun,
                         @Min(1) int maxAnomaliesPerRun) {}

  public record TokenBudget(boolean enabled, @Min(1000) int defaultDailyBudget,
                            boolean hardFailOnExceed) {}
}
```

### 28.4 Persistence for AI usage and idempotency

Add these tables/entities for observability and control:

```java
@Entity
@Table(name = "ai_usage_logs")
@Getter
@Setter
public class AiUsageLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String userId;

  @Column(nullable = false, length = 32)
  private String feature; // OCR | INSIGHTS | ANOMALY

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(nullable = false)
  private Integer promptTokens;

  @Column(nullable = false)
  private Integer completionTokens;

  @Column(nullable = false)
  private Integer totalTokens;

  @Column(nullable = false)
  private boolean success;

  @Column(length = 500)
  private String errorCode;

  @Column(nullable = false)
  private Instant timestamp;
}
```

```java
@Entity
@Table(name = "ai_job_runs")
@Getter
@Setter
public class AiJobRunEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 64)
  private String jobName; // RECOMMENDATION_GENERATION | ANOMALY_DETECTION

  @Column(nullable = false, length = 32)
  private String status; // STARTED | SUCCESS | FAILED

  @Column(nullable = false)
  private Instant startedAt;

  @Column
  private Instant finishedAt;

  @Column(length = 500)
  private String errorMessage;
}
```

```java
public interface AiUsageLogRepository extends JpaRepository<AiUsageLogEntity, UUID> {
  @Query("""
    select coalesce(sum(a.totalTokens), 0)
    from AiUsageLogEntity a
    where a.userId = :userId and a.timestamp >= :start and a.timestamp < :end and a.success = true
  """)
  Integer sumDailyTokens(@Param("userId") String userId,
                         @Param("start") Instant start,
                         @Param("end") Instant end);
}

public interface AiJobRunRepository extends JpaRepository<AiJobRunEntity, UUID> {
  Optional<AiJobRunEntity> findTopByJobNameOrderByStartedAtDesc(String jobName);
}
```

### 28.5 Provider adapter contracts

```java
public interface OcrProvider {
  OcrProviderResult extract(MultipartFile file, OcrProviderContext context);
}

public interface InsightProvider {
  InsightProviderResult generateRecommendations(InsightProviderContext context);
}

public interface AnomalyProvider {
  AnomalyProviderResult detect(AnomalyProviderContext context);
}

public record OcrProviderResult(List<OcrMaterialLineResponse> lines,
                                int promptTokens,
                                int completionTokens,
                                BigDecimal confidence) {}
```

Context models:
- Include `workspaceId`, `userId`, `requestId`, and sanitized input summary.
- Never pass unnecessary PII to provider prompts.

### 28.6 Gemini OCR provider skeleton

```java
@Component
@RequiredArgsConstructor
public class GeminiOcrProvider implements OcrProvider {

  private final GeminiClient geminiClient;
  private final AiPromptFactory aiPromptFactory;

  @Override
  public OcrProviderResult extract(MultipartFile file, OcrProviderContext context) {
    String prompt = aiPromptFactory.ocrPrompt();
    GeminiResponse response = geminiClient.extractStructuredOcr(prompt, file);

    List<OcrMaterialLineResponse> lines = response.lines().stream()
      .map(this::normalizeLine)
      .toList();

    return new OcrProviderResult(
      lines,
      response.promptTokens(),
      response.completionTokens(),
      response.confidence()
    );
  }

  private OcrMaterialLineResponse normalizeLine(GeminiLine line) {
    BigDecimal quantity = line.quantity() == null ? BigDecimal.ZERO : line.quantity().max(new BigDecimal("0.01"));
    BigDecimal price = line.price() == null ? BigDecimal.ZERO : line.price().max(new BigDecimal("0.00"));

    return new OcrMaterialLineResponse(
      UUID.randomUUID().toString(),
      Optional.ofNullable(line.materialName()).orElse("").trim(),
      quantity,
      Optional.ofNullable(line.unit()).orElse("").trim(),
      price
    );
  }
}
```

### 28.7 OCR service orchestration (validation + token budget + fallback)

```java
@Service
@RequiredArgsConstructor
public class OcrService {

  private final SecurityProps securityProps;
  private final AiProps aiProps;
  private final OcrProvider ocrProvider;
  private final TokenBudgetService tokenBudgetService;
  private final AiUsageAuditService usageAuditService;

  @Transactional
  public List<OcrMaterialLineResponse> processOcr(MultipartFile file, String userId, String requestId) {
    validateUpload(file);

    if (!aiProps.ocr().enabled()) {
      throw new BusinessException("OCR is disabled by configuration.");
    }

    tokenBudgetService.assertAllowed(userId, "OCR");

    try {
      OcrProviderResult providerResult = ocrProvider.extract(file, new OcrProviderContext(userId, requestId));

      List<OcrMaterialLineResponse> filtered = providerResult.lines().stream()
        .filter(line -> !line.materialName().isBlank())
        .toList();

      usageAuditService.recordSuccess(userId, "OCR", aiProps.provider(),
        providerResult.promptTokens(), providerResult.completionTokens());

      return filtered;
    } catch (Exception ex) {
      usageAuditService.recordFailure(userId, "OCR", aiProps.provider(), "OCR_PROVIDER_ERROR");
      throw ex;
    }
  }

  private void validateUpload(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new BusinessException("Image file is required.");
    if (!securityProps.allowedUploadContentTypes().contains(file.getContentType())) {
      throw new BusinessException("Unsupported image content type.");
    }
    long maxBytes = (long) aiProps.ocr().maxImageSizeMb() * 1024 * 1024;
    if (file.getSize() > maxBytes) throw new BusinessException("Image exceeds max allowed size.");
  }
}
```

### 28.8 Recommendation/anomaly generation pipeline

Recommended flow per scheduled run:
1. Load operations window (e.g., last 7 days).
2. Compute deterministic baseline features in backend first.
3. Call AI provider only for narrative recommendation enrichment if needed.
4. Upsert `insights` and `anomalies` with stable status defaults (`new`).
5. Mark previous stale records as `ignored` or keep history by timestamp strategy.

Scheduler skeleton:

```java
@Component
@RequiredArgsConstructor
public class AiScheduler {

  private final AiProps aiProps;
  private final RecommendationService recommendationService;
  private final AnomalyService anomalyService;

  @Scheduled(cron = "${wastepilot.ai.insights.schedule-cron}")
  public void runInsightJobs() {
    if (!aiProps.insights().enabled()) {
      return;
    }

    recommendationService.generateAndPersist();
    anomalyService.generateAndPersist();
  }
}
```

### 28.9 Prompting and output contract strategy

Prompting rules:
- Keep prompt templates versioned (e.g., `OCR_PROMPT_V1`, `INSIGHT_PROMPT_V2`).
- Store prompt version in `ai_usage_logs` for traceability.
- Force provider outputs into strict JSON schema.
- Reject and retry once if schema parse fails.

Parser policy:
- Fail closed for malformed provider output in write flows.
- Never persist partially parsed recommendation objects.

### 28.10 Token budget implementation details

Daily budget resolution order:
1. `UserSettings.dailyTokenBudget` if present.
2. `wastepilot.ai.token-budget.default-daily-budget` fallback.

Budget algorithm:
- Compute UTC day window `[00:00, 24:00)`.
- Sum successful token usage from `ai_usage_logs`.
- Reject if next call exceeds budget.
- If `hard-fail-on-exceed=false`, return deterministic fallback response.

Snippet:

```java
@Service
@RequiredArgsConstructor
public class TokenBudgetService {

  private final AiUsageLogRepository usageLogRepository;
  private final UserSettingsRepository userSettingsRepository;
  private final AiProps aiProps;

  public void assertAllowed(String userId, String feature) {
    if (!aiProps.tokenBudget().enabled()) {
      return;
    }

    int budget = userSettingsRepository.findByUserId(userId)
      .map(UserSettingsEntity::getDailyTokenBudget)
      .orElse(aiProps.tokenBudget().defaultDailyBudget());

    Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant end = start.plus(1, ChronoUnit.DAYS);
    int used = Optional.ofNullable(usageLogRepository.sumDailyTokens(userId, start, end)).orElse(0);

    if (used >= budget) {
      throw new BusinessException("Daily AI token budget exceeded for feature: " + feature);
    }
  }
}
```

### 28.11 Reliability and resilience (must-have)

Use resilience controls for all provider calls:
- request timeout (`wastepilot.ai.request-timeout-ms`)
- bounded retry (`max-retries`)
- circuit breaker around provider adapter
- fallback behavior for non-critical paths

Recommendations:
- OCR endpoint should fail fast with readable message.
- Insight generation jobs should mark run status `FAILED` and continue next schedule.
- Never block core operational writes when AI provider is unavailable.

### 28.12 AI-specific observability

Track metrics:
- `ai_ocr_requests_total`, `ai_ocr_failures_total`
- `ai_provider_latency_ms` (by provider, feature)
- `ai_tokens_used_total` (by user/day/feature)
- `ai_job_duration_ms` and `ai_job_failures_total`
- `ai_parse_failures_total`

Log fields:
- requestId, userId, feature, provider, promptVersion, tokenUsage, latencyMs, success.

### 28.13 AI test strategy

Unit tests:
1. OCR line normalization and validation.
2. Token budget exceed logic.
3. Prompt output parser strictness.

Integration tests:
1. `/api/v1/ai/ocr` accepts valid image and rejects invalid content type.
2. Budget exceeded path returns expected error/fallback.
3. Scheduler persists insights/anomalies and usage logs.

Contract tests:
1. OCR response fields exactly match frontend expectations.
2. Insight/anomaly status enums stay stable.

### 28.14 AI rollout stages

Stage 1:
- Enable OCR in low traffic environment.
- Keep recommendations as deterministic rule-based only.

Stage 2:
- Enable AI-enriched recommendations.
- Track token/cost and parse quality.

Stage 3:
- Enable anomaly model/hybrid logic.
- Add alerting on AI failure and budget spikes.

Stage 4:
- Production hardening with SLOs, circuit breakers, and failover provider strategy.

### 28.15 AI implementation checklist

1. `AiProps` and `SecurityProps` configured and validated.
2. Provider adapters implemented with strict schema parsing.
3. OCR upload hardening active.
4. `ai_usage_logs` and `ai_job_runs` tables migrated.
5. Token budget enforcement active.
6. Scheduler for insights/anomalies active.
7. Metrics and logs wired to observability stack.
8. Fallback behavior documented for provider outage.
## 29. Production-Ready Enhancements (Added)

This section details critical enhancements required for transitioning the architecture to a production-grade state.

### 29.1 Pagination and Sorting

For lists that grow indefinitely (e.g., Activity Logs, Inventory Logs, Batches), using `findTop200` is insufficient. Implement `Pageable` in repositories and return a `PagedResponse` DTO.

**DTO Contract:**
```java
public record PagedResponse<T>(
  List<T> content,
  int pageNumber,
  int pageSize,
  long totalElements,
  int totalPages,
  boolean isLast
) {}
```

**Controller Example:**
```java
@GetMapping("/inventory-logs")
public PagedResponse<InventoryLogResponse> getLogs(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {
  Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
  Page<InventoryLogEntity> entityPage = inventoryLogRepository.findAll(pageable);
  List<InventoryLogResponse> content = entityPage.getContent().stream()
      .map(inventoryMapper::toResponse).toList();
  return new PagedResponse<>(content, entityPage.getNumber(), entityPage.getSize(),
      entityPage.getTotalElements(), entityPage.getTotalPages(), entityPage.isLast());
}
```

### 29.2 Global Exception Handling (@RestControllerAdvice)

To fulfill the Error Handling Contract (Section 16), use a global advice class to catch business exceptions and format them properly.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
    ApiErrorResponse error = new ApiErrorResponse(
        Instant.now(), 400, "BAD_REQUEST", ex.getMessage(), request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> e.getField() + " " + e.getDefaultMessage())
        .findFirst().orElse("Validation failed");
    ApiErrorResponse error = new ApiErrorResponse(
        Instant.now(), 400, "VALIDATION_ERROR", message, request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }
}
```

### 29.3 Current User Extraction Context

Instead of passing the `actor` string down from the Controller to every Service method manually, encapsulate identity inside a `SecurityUtils` context block.

```java
@Component
public class SecurityUtils {
  public static String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return jwt.getSubject(); // standard approach for JWT
    }
    return "SYSTEM"; // fallback for internal jobs
  }
}
```
*Usage in service*: `String actor = SecurityUtils.getCurrentUserId();`

### 29.4 Analytics & Dashboard Query Strategy

To populate the `AnalyticsPayloadResponse` efficiently without loading thousands of rows into Java memory, use JPQL grouping or native queries.

**Example (Circularity Score Trend by Week):**
```java
@Query("SELECT new com.project.wastepilot.dto.CircularityPoint( " +
       "  FUNCTION('YEARWEEK', b.closedAt, 1), " + // MySQL specific formatting
       "  AVG(b.confidenceScore) " + // Simplified calculation
       ") " +
       "FROM BatchEntity b " +
       "WHERE b.status = 'completed' AND b.closedAt >= :since " +
       "GROUP BY FUNCTION('YEARWEEK', b.closedAt, 1) " +
       "ORDER BY FUNCTION('YEARWEEK', b.closedAt, 1) ASC")
List<CircularityPoint> getWeeklyCircularityTrend(@Param("since") Instant since);
```
*Note: For complex reporting, creating a dedicated read-model or materialized view in MySQL is recommended.*

### 29.5 Database Migration (Flyway)

**✅ Flyway has been successfully configured and implemented.**

We have replaced `ddl-auto=update` with Flyway database migrations for production safety. 
- Flyway dependencies (`flyway-core`, `flyway-mysql`) were added to `pom.xml`.
- Configuration (`spring.flyway.enabled=true`, `spring.flyway.baseline-on-migrate=true`) was added to `application.properties`.
- The baseline migration script `V1__init_schema.sql` was created in `src/main/resources/db/migration/` containing the exact DDL statements corresponding to the JPA entities.

This ensures that any future schema changes are versioned, reproducible, and tracked alongside the application code, mitigating risk during deployments.
