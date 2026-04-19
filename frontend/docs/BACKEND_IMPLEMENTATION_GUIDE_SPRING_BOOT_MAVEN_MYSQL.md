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
- Flyway
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
    application.yml
    application-dev.yml
    application-prod.yml
    db/migration/
      V1__init_schema.sql
      V2__seed_reference_data.sql
  src/test/java/
```

## 4. Maven Configuration

Use this as baseline dependencies:

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.wastepilot</groupId>
  <artifactId>wastepilot-backend</artifactId>
  <version>0.0.1-SNAPSHOT</version>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
  </parent>

  <properties>
    <java.version>21</java.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
      <groupId>mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.mapstruct</groupId>
      <artifactId>mapstruct</artifactId>
      <version>${mapstruct.version}</version>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
      <version>2.6.0</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>mysql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <source>${java.version}</source>
          <target>${java.version}</target>
          <annotationProcessorPaths>
            <path>
              <groupId>org.mapstruct</groupId>
              <artifactId>mapstruct-processor</artifactId>
              <version>${mapstruct.version}</version>
            </path>
            <path>
              <groupId>org.projectlombok</groupId>
              <artifactId>lombok</artifactId>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

## 5. Configuration (application.yml)

Use UTC as canonical server timezone.

```yaml
server:
  port: 8080

spring:
  application:
    name: wastepilot-backend
  datasource:
    url: jdbc:mysql://localhost:3306/wastepilot?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: wastepilot
    password: wastepilot
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

wastepilot:
  ops:
    close-variance-threshold: 5
  security:
    cors-allowed-origins:
      - http://localhost:5173
  ai:
    ocr-provider: gemini
    recommendations-enabled: true
```

Profile examples:

```yaml
# application-dev.yml
spring:
  jpa:
    hibernate:
      ddl-auto: update

# application-prod.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
logging:
  level:
    root: INFO
```

DDL guidance:
- Local dev only: ddl-auto=update is acceptable for speed.
- Shared/prod environments: use Flyway and keep ddl-auto=validate.

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
- Role-based authorization (e.g., ROLE_OPERATOR, ROLE_MANAGER, ROLE_ADMIN)
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
      .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("OPERATOR", "MANAGER", "ADMIN")
      .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAnyRole("OPERATOR", "MANAGER", "ADMIN")
      .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasAnyRole("MANAGER", "ADMIN")
      .requestMatchers(HttpMethod.PATCH, "/api/v1/**").hasAnyRole("OPERATOR", "MANAGER", "ADMIN")
      .anyRequest().authenticated()
    )
    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

  return http.build();
}
```

## 13. AI Integration Plan

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

### 14.1 Dockerfile

```dockerfile
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/wastepilot-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 14.2 docker-compose.yml

```yaml
version: "3.9"
services:
  mysql:
    image: mysql:8.4
    container_name: wastepilot-mysql
    environment:
      MYSQL_DATABASE: wastepilot
      MYSQL_USER: wastepilot
      MYSQL_PASSWORD: wastepilot
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  api:
    build: .
    container_name: wastepilot-api
    depends_on:
      - mysql
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/wastepilot?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      SPRING_DATASOURCE_USERNAME: wastepilot
      SPRING_DATASOURCE_PASSWORD: wastepilot
    ports:
      - "8080:8080"

volumes:
  mysql_data:
```

## 15. Flyway Migration Starter

Example V1 migration outline:

```sql
CREATE TABLE materials (...);
CREATE TABLE templates (...);
CREATE TABLE template_lines (...);
CREATE TABLE batches (...);
CREATE TABLE inventory_logs (...);
CREATE TABLE waste_logs (...);
CREATE TABLE activity_logs (...);
CREATE TABLE audit_trail (...);
CREATE TABLE red_flags (...);
CREATE TABLE insights (...);
CREATE TABLE anomalies (...);
CREATE TABLE user_settings (...);
```

Guidelines:
- Keep all timestamps in UTC
- Add indexes for batchId, timestamp, status
- Add unique constraints where domain requires (e.g., material name)

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
- project skeleton, Flyway, security baseline
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
