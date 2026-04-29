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

Minimum entity set:
- MaterialEntity
- TemplateEntity
- TemplateLineEntity
- BatchEntity
- InventoryLogEntity
- WasteLogEntity
- ActivityLogEntity
- AuditTrailEntity
- RedFlagEntity
- InsightEntity
- AnomalyEntity
- UserSettingsEntity
- AiUsageLogEntity (AI usage logs)
- AiJobRunEntity (AI job runs)

Example entity:

```java
@Entity
@Table(name = "materials")
@Getter
@Setter
public class MaterialEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 160)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MaterialCategory category;

  @Column(nullable = false, length = 16)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 1)
  private CircularGrade circularGrade;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal stock;

  @Column(nullable = false, length = 120)
  private String supplier;
}
```

## 7. DTO Design

Split request/response DTOs from entities.

Examples:

```java
public record CreateBatchRequest(
        @NotBlank String templateName,
        @DecimalMin(value = "0.1") BigDecimal outputUnits,
        @DecimalMin(value = "0.0") BigDecimal wasteKg
) {}

public record BatchResponse(
        String id,
        String templateName,
        Instant startedAt,
        BigDecimal outputUnits,
        BigDecimal wasteKg,
        String status,
        Instant closedAt,
        String closedBy,
        String closeReason
) {}

public record CloseBatchRequest(
        @NotBlank String batchId,
        @DecimalMin(value = "0.1") BigDecimal outputUnits,
        String closeReason
) {}
```

For status patch endpoints:

```java
public record StatusPatchRequest(@NotBlank String status) {}
```

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

Define repositories per aggregate.

```java
public interface MaterialRepository extends JpaRepository<MaterialEntity, UUID> {
  Optional<MaterialEntity> findByNameIgnoreCase(String name);
}

public interface BatchRepository extends JpaRepository<BatchEntity, UUID> {
  List<BatchEntity> findByStatusOrderByStartedAtDesc(BatchStatus status);
}

public interface WasteLogRepository extends JpaRepository<WasteLogEntity, UUID> {
  List<WasteLogEntity> findByBatchIdOrderByTimestampDesc(UUID batchId);
}
```

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

This section provides copy-ready starter snippets for backend initialization.

Notes:
- Use these as baseline and adapt package names.
- Keep field names aligned with frontend contract casing.
- All timestamps should be stored in UTC.

### 23.1 Shared enums

```java
public enum MaterialCategory { Recyclable, Organic, Hazardous, Packaging }
public enum CircularGrade { A, B, C }
public enum BatchStatus { running, completed }
public enum InventoryType { IN, OUT }
public enum WasteDestination { reuse, repair, dispose }
public enum RecoveryStatus { pending, converted, not_applicable }
public enum InsightStatus { new, applied, ignored }
public enum RedFlagSeverity { low, medium, high }
public enum EntityType { batch, inventory, waste, material, template, insight, anomaly, settings }
```

### 23.2 Shared base entity (optional but recommended)

```java
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
```

### 23.3 Entity snippets (all required for project init)

#### MaterialEntity

```java
@Entity
@Table(name = "materials")
@Getter
@Setter
public class MaterialEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 160)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MaterialCategory category;

  @Column(nullable = false, length = 16)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 1)
  private CircularGrade circularGrade;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal stock;

  @Column(nullable = false, length = 120)
  private String supplier;
}
```

#### TemplateEntity

```java
@Entity
@Table(name = "templates")
@Getter
@Setter
public class TemplateEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, unique = true, length = 80)
  private String sku;

  @Column(name = "expected_waste_kg", nullable = false, precision = 14, scale = 3)
  private BigDecimal expectedWasteKg;

  @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TemplateLineEntity> lines = new ArrayList<>();
}
```

#### TemplateLineEntity

```java
@Entity
@Table(name = "template_lines")
@Getter
@Setter
public class TemplateLineEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "template_id", nullable = false)
  private TemplateEntity template;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "material_id", nullable = false)
  private MaterialEntity material;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, length = 16)
  private String unit;
}
```

#### BatchEntity

```java
@Entity
@Table(name = "batches")
@Getter
@Setter
public class BatchEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_name", nullable = false, length = 160)
  private String templateName;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "output_units", nullable = false, precision = 14, scale = 3)
  private BigDecimal outputUnits;

  @Column(name = "waste_kg", nullable = false, precision = 14, scale = 3)
  private BigDecimal wasteKg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BatchStatus status;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by", length = 120)
  private String closedBy;

  @Column(name = "close_reason", length = 500)
  private String closeReason;
}
```

#### InventoryLogEntity

```java
@Entity
@Table(name = "inventory_logs")
@Getter
@Setter
public class InventoryLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id")
  private BatchEntity batch;

  @Column(name = "material_name", nullable = false, length = 160)
  private String materialName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private InventoryType type;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, length = 16)
  private String unit;

  @Column(nullable = false, length = 32)
  private String source;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### WasteLogEntity

```java
@Entity
@Table(name = "waste_logs")
@Getter
@Setter
public class WasteLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false)
  private BatchEntity batch;

  @Column(name = "material_name", nullable = false, length = 160)
  private String materialName;

  @Column(name = "quantity_kg", nullable = false, precision = 14, scale = 3)
  private BigDecimal quantityKg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private WasteDestination destination;

  @Enumerated(EnumType.STRING)
  @Column(name = "recovery_status", nullable = false, length = 24)
  private RecoveryStatus recoveryStatus;

  @Column(name = "ai_suggested_action", length = 500)
  private String aiSuggestedAction;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### ActivityLogEntity

```java
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
public class ActivityLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EntityType entity;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(nullable = false, length = 160)
  private String actor;

  @Column(nullable = false, length = 500)
  private String detail;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### AuditTrailEntity

```java
@Entity
@Table(name = "audit_trail")
@Getter
@Setter
public class AuditTrailEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EntityType entity;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(nullable = false, length = 64)
  private String field;

  @Column(name = "old_value", length = 500)
  private String oldValue;

  @Column(name = "new_value", length = 500)
  private String newValue;

  @Column(nullable = false, length = 160)
  private String actor;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### RedFlagEntity

```java
@Entity
@Table(name = "red_flags")
@Getter
@Setter
public class RedFlagEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RedFlagSeverity severity;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  @Column(nullable = false)
  private boolean resolved;

  @Column(name = "related_batch_id")
  private UUID relatedBatchId;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;
}
```

#### InsightEntity

```java
@Entity
@Table(name = "insights")
@Getter
@Setter
public class InsightEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1500)
  private String content;

  @Column(name = "impact_category", nullable = false, length = 32)
  private String impactCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InsightStatus status;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### AnomalyEntity

```java
@Entity
@Table(name = "anomalies")
@Getter
@Setter
public class AnomalyEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String process;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal zScore;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal wasteKg;

  @Column(nullable = false, length = 32)
  private String date;

  @Column(nullable = false, length = 1000)
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InsightStatus status;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### UserSettingsEntity

```java
@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettingsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 160)
  private String userId;

  @Column(nullable = false, length = 200)
  private String company;

  @Column(nullable = false, length = 160)
  private String email;

  @Column(nullable = false, length = 80)
  private String role;

  @Column(name = "daily_token_budget", nullable = false)
  private Integer dailyTokenBudget;

  @Column(name = "notify_anomalies", nullable = false)
  private boolean notifyAnomalies;

  @Column(name = "notify_recommendations", nullable = false)
  private boolean notifyRecommendations;

  @Column(name = "notify_overdue_batches", nullable = false)
  private boolean notifyOverdueBatches;
}
```

#### AiUsageLogEntity

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

#### AiJobRunEntity

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

### 23.4 Full entity code (copy-ready)

Use the same package layout described in Section 3. The blocks below omit the `package` line so you can paste into the correct domain folder.

#### MaterialEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "materials")
@Getter
@Setter
public class MaterialEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 160)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MaterialCategory category;

  @Column(nullable = false, length = 16)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 1)
  private CircularGrade circularGrade;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal stock;

  @Column(nullable = false, length = 120)
  private String supplier;
}
```

#### TemplateEntity

```java
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "templates")
@Getter
@Setter
public class TemplateEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, unique = true, length = 80)
  private String sku;

  @Column(name = "expected_waste_kg", nullable = false, precision = 14, scale = 3)
  private BigDecimal expectedWasteKg;

  @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TemplateLineEntity> lines = new ArrayList<>();
}
```

#### TemplateLineEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "template_lines")
@Getter
@Setter
public class TemplateLineEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "template_id", nullable = false)
  private TemplateEntity template;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "material_id", nullable = false)
  private MaterialEntity material;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, length = 16)
  private String unit;
}
```

#### BatchEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "batches")
@Getter
@Setter
public class BatchEntity extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_name", nullable = false, length = 160)
  private String templateName;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "output_units", nullable = false, precision = 14, scale = 3)
  private BigDecimal outputUnits;

  @Column(name = "waste_kg", nullable = false, precision = 14, scale = 3)
  private BigDecimal wasteKg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BatchStatus status;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by", length = 120)
  private String closedBy;

  @Column(name = "close_reason", length = 500)
  private String closeReason;
}
```

#### InventoryLogEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventory_logs")
@Getter
@Setter
public class InventoryLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id")
  private BatchEntity batch;

  @Column(name = "material_name", nullable = false, length = 160)
  private String materialName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private InventoryType type;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, length = 16)
  private String unit;

  @Column(nullable = false, length = 32)
  private String source;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### WasteLogEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "waste_logs")
@Getter
@Setter
public class WasteLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false)
  private BatchEntity batch;

  @Column(name = "material_name", nullable = false, length = 160)
  private String materialName;

  @Column(name = "quantity_kg", nullable = false, precision = 14, scale = 3)
  private BigDecimal quantityKg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private WasteDestination destination;

  @Enumerated(EnumType.STRING)
  @Column(name = "recovery_status", nullable = false, length = 24)
  private RecoveryStatus recoveryStatus;

  @Column(name = "ai_suggested_action", length = 500)
  private String aiSuggestedAction;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### ActivityLogEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
public class ActivityLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EntityType entity;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(nullable = false, length = 160)
  private String actor;

  @Column(nullable = false, length = 500)
  private String detail;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### AuditTrailEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_trail")
@Getter
@Setter
public class AuditTrailEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EntityType entity;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(nullable = false, length = 64)
  private String field;

  @Column(name = "old_value", length = 500)
  private String oldValue;

  @Column(name = "new_value", length = 500)
  private String newValue;

  @Column(nullable = false, length = 160)
  private String actor;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### RedFlagEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "red_flags")
@Getter
@Setter
public class RedFlagEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RedFlagSeverity severity;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  @Column(nullable = false)
  private boolean resolved;

  @Column(name = "related_batch_id")
  private UUID relatedBatchId;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;
}
```

#### InsightEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "insights")
@Getter
@Setter
public class InsightEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1500)
  private String content;

  @Column(name = "impact_category", nullable = false, length = 32)
  private String impactCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InsightStatus status;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### AnomalyEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "anomalies")
@Getter
@Setter
public class AnomalyEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String process;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal zScore;

  @Column(nullable = false, precision = 14, scale = 3)
  private BigDecimal wasteKg;

  @Column(nullable = false, length = 32)
  private String date;

  @Column(nullable = false, length = 1000)
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InsightStatus status;

  @Column(nullable = false)
  private Instant timestamp;
}
```

#### UserSettingsEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettingsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 160)
  private String userId;

  @Column(nullable = false, length = 200)
  private String company;

  @Column(nullable = false, length = 160)
  private String email;

  @Column(nullable = false, length = 80)
  private String role;

  @Column(name = "daily_token_budget", nullable = false)
  private Integer dailyTokenBudget;

  @Column(name = "notify_anomalies", nullable = false)
  private boolean notifyAnomalies;

  @Column(name = "notify_recommendations", nullable = false)
  private boolean notifyRecommendations;

  @Column(name = "notify_overdue_batches", nullable = false)
  private boolean notifyOverdueBatches;
}
```

#### AiUsageLogEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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
  private String feature;

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

#### AiJobRunEntity

```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_job_runs")
@Getter
@Setter
public class AiJobRunEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 64)
  private String jobName;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(nullable = false)
  private Instant startedAt;

  @Column
  private Instant finishedAt;

  @Column(length = 500)
  private String errorMessage;
}
```
```

## 24. DTO Snippets (contract-ready starter)

### 24.1 Common

```java
public record StatusPatchRequest(@NotBlank String status) {}

public record ApiErrorResponse(
  Instant timestamp,
  int status,
  String code,
  String message,
  String path
) {}
```

### 24.2 Operations DTOs

```java
public record CreateBatchRequest(
  @NotBlank String templateName,
  @DecimalMin("0.1") BigDecimal outputUnits,
  @DecimalMin("0.0") BigDecimal wasteKg
) {}

public record BatchResponse(
  String id,
  String templateName,
  Instant startedAt,
  BigDecimal outputUnits,
  BigDecimal wasteKg,
  String status,
  Instant closedAt,
  String closedBy,
  String closeReason
) {}

public record CreateInventoryLogRequest(
  String batchId,
  @NotBlank String materialName,
  @NotBlank String type,
  @DecimalMin("0.001") BigDecimal quantity,
  @NotBlank String unit,
  @NotBlank String source
) {}

public record InventoryLogResponse(
  String id,
  String batchId,
  String materialName,
  String type,
  BigDecimal quantity,
  String unit,
  String source,
  Instant timestamp
) {}

public record CreateWasteLogRequest(
  @NotBlank String batchId,
  @NotBlank String materialName,
  @DecimalMin("0.001") BigDecimal quantityKg,
  @NotBlank String destination
) {}

public record WasteLogResponse(
  String id,
  String batchId,
  String materialName,
  BigDecimal quantityKg,
  String destination,
  String recoveryStatus,
  String aiSuggestedAction,
  Instant timestamp
) {}

public record RecoverWasteRequest(@NotBlank String wasteLogId) {}

public record WasteRecoveryResponse(
  WasteLogResponse wasteLog,
  InventoryLogResponse inventoryLog
) {}

public record BatchCloseSummaryResponse(
  String batchId,
  BigDecimal outputUnits,
  BigDecimal expectedWasteKg,
  BigDecimal actualWasteKg,
  BigDecimal variance,
  String confidenceLevel,
  BigDecimal confidenceScore,
  BigDecimal landfillShare
) {}

public record CloseBatchRequest(
  @NotBlank String batchId,
  @DecimalMin("0.1") BigDecimal outputUnits,
  String closeReason
) {}

public record OperationsPayloadResponse(
  List<BatchResponse> batches,
  List<InventoryLogResponse> inventoryLogs,
  List<WasteLogResponse> wasteLogs
) {}
```

### 24.3 Material + Template DTOs

```java
public record UpsertMaterialRequest(
  String id,
  @NotBlank String name,
  @NotBlank String category,
  @NotBlank String unit,
  @NotBlank String circularGrade,
  @DecimalMin("0.0") BigDecimal stock,
  @NotBlank String supplier
) {}

public record MaterialResponse(
  String id,
  String name,
  String category,
  String unit,
  String circularGrade,
  BigDecimal stock,
  String supplier
) {}

public record TemplateLineRequest(
  @NotBlank String materialId,
  @NotBlank String materialName,
  @DecimalMin("0.001") BigDecimal quantity,
  @NotBlank String unit
) {}

public record UpsertTemplateRequest(
  String id,
  @NotBlank String name,
  @NotBlank String sku,
  @DecimalMin("0.0") BigDecimal expectedWasteKg,
  @NotEmpty List<TemplateLineRequest> lines
) {}

public record TemplateLineResponse(
  String materialId,
  String materialName,
  BigDecimal quantity,
  String unit
) {}

public record TemplateResponse(
  String id,
  String name,
  String sku,
  BigDecimal expectedWasteKg,
  Instant updatedAt,
  List<TemplateLineResponse> lines
) {}
```

### 24.4 Insight + anomaly DTOs

```java
public record CircularInsightResponse(
  String id,
  String title,
  String content,
  String impactCategory,
  String status,
  Instant timestamp
) {}

public record AnomalyResponse(
  String id,
  String process,
  BigDecimal zScore,
  BigDecimal wasteKg,
  String date,
  String note,
  String status,
  Instant timestamp
) {}

public record InsightsPayloadResponse(
  List<CircularInsightResponse> recommendations,
  List<AnomalyResponse> anomalies
) {}
```

### 24.5 Analytics + reports DTOs

```java
public record CircularityPoint(String week, BigDecimal score) {}
public record WasteBreakdownItem(String category, BigDecimal value) {}
public record EfficiencyItem(String material, BigDecimal efficiency) {}
public record LandfillSharePoint(String week, BigDecimal share) {}
public record LandfillIntensityPoint(String week, BigDecimal kgPerUnit) {}

public record AnalyticsPayloadResponse(
  List<CircularityPoint> circularityTrend,
  List<WasteBreakdownItem> wasteBreakdown,
  List<EfficiencyItem> efficiencyByMaterial,
  List<LandfillSharePoint> landfillShareTrend,
  List<LandfillIntensityPoint> landfillIntensityTrend
) {}

public record ReportSummaryResponse(
  BigDecimal circularScoreAvg,
  Integer totalActivities,
  BigDecimal onTimeCloseRate,
  BigDecimal totalInventoryIn,
  BigDecimal totalInventoryOut,
  BigDecimal recoveredWasteKg,
  BigDecimal landfillWasteKg
) {}

public record ReportTrendPoint(
  String label,
  BigDecimal circularScore,
  BigDecimal recoveredKg,
  BigDecimal landfillKg
) {}

public record ReportTopAction(String action, Integer count) {}
public record ReportTopContributor(String name, BigDecimal value) {}

public record ReportsPayloadResponse(
  String period,
  String windowLabel,
  ReportSummaryResponse summary,
  List<ReportTrendPoint> trend,
  List<ReportTopAction> topActions,
  List<ReportTopContributor> topContributors,
  List<String> highlights
) {}
```

### 24.6 OCR + settings + integrity DTOs

```java
public record OcrMaterialLineResponse(
  String id,
  String materialName,
  BigDecimal quantity,
  String unit,
  BigDecimal price
) {}

public record UserSettingsResponse(
  String company,
  String email,
  String role,
  Integer dailyTokenBudget,
  boolean notifyAnomalies,
  boolean notifyRecommendations,
  boolean notifyOverdueBatches
) {}

public record UpdateUserSettingsRequest(
  @NotBlank String company,
  @Email @NotBlank String email,
  @NotBlank String role,
  @Min(1) @Max(100000) Integer dailyTokenBudget,
  boolean notifyAnomalies,
  boolean notifyRecommendations,
  boolean notifyOverdueBatches
) {}

public record ActivityLogEntryResponse(
  String id,
  String entity,
  String entityId,
  String action,
  String actor,
  String detail,
  Instant timestamp
) {}

public record AuditTrailEntryResponse(
  String id,
  String entity,
  String entityId,
  String field,
  String oldValue,
  String newValue,
  String actor,
  Instant timestamp
) {}

public record IntegrityOverviewResponse(
  BigDecimal averageConfidenceScore,
  Integer openRedFlags,
  Integer postScoreEdits,
  Integer overdueBatchClosures
) {}
```

## 25. Repository snippets (required baseline)

```java
public interface MaterialRepository extends JpaRepository<MaterialEntity, UUID> {
  Optional<MaterialEntity> findByNameIgnoreCase(String name);
  boolean existsByNameIgnoreCase(String name);
}

public interface TemplateRepository extends JpaRepository<TemplateEntity, UUID> {
  Optional<TemplateEntity> findBySkuIgnoreCase(String sku);
}

public interface TemplateLineRepository extends JpaRepository<TemplateLineEntity, UUID> {
  List<TemplateLineEntity> findByTemplateId(UUID templateId);
}

public interface BatchRepository extends JpaRepository<BatchEntity, UUID> {
  List<BatchEntity> findByStatusOrderByStartedAtDesc(BatchStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BatchEntity b where b.id = :id")
  Optional<BatchEntity> lockById(@Param("id") UUID id);
}

public interface InventoryLogRepository extends JpaRepository<InventoryLogEntity, UUID> {
  List<InventoryLogEntity> findByBatchIdOrderByTimestampDesc(UUID batchId);
  List<InventoryLogEntity> findByMaterialNameIgnoreCaseOrderByTimestampDesc(String materialName);
  List<InventoryLogEntity> findTop200ByOrderByTimestampDesc();
}

public interface WasteLogRepository extends JpaRepository<WasteLogEntity, UUID> {
  List<WasteLogEntity> findByBatchIdOrderByTimestampDesc(UUID batchId);
  List<WasteLogEntity> findTop200ByOrderByTimestampDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WasteLogEntity w where w.id = :id")
  Optional<WasteLogEntity> lockById(@Param("id") UUID id);
}

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntity, UUID> {
  List<ActivityLogEntity> findTop200ByOrderByTimestampDesc();
}

public interface AuditTrailRepository extends JpaRepository<AuditTrailEntity, UUID> {
  List<AuditTrailEntity> findTop200ByOrderByTimestampDesc();
}

public interface RedFlagRepository extends JpaRepository<RedFlagEntity, UUID> {
  long countByResolvedFalse();
}

public interface InsightRepository extends JpaRepository<InsightEntity, UUID> {
  List<InsightEntity> findAllByOrderByTimestampDesc();
  List<InsightEntity> findByStatus(InsightStatus status);
}

public interface AnomalyRepository extends JpaRepository<AnomalyEntity, UUID> {
  List<AnomalyEntity> findAllByOrderByTimestampDesc();
  List<AnomalyEntity> findByStatus(InsightStatus status);
}

public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, UUID> {
  Optional<UserSettingsEntity> findByUserId(String userId);
}
```

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