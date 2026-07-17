# Milestone 31 Promotion And Performance Operations Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build store and administrator operations dashboards backed by a transactional outbox, idempotent asynchronous projections, auditable campaign commands, outcome and inventory metrics, and registered M30 performance evidence.

**Architecture:** OLTP commands persist versioned operational events in a PostgreSQL outbox in the same transaction. A database poller applies at-least-once events to generation-scoped read models through transport-neutral handlers; store/admin APIs read only those projections, while validated M30 measurement snapshots provide performance evidence. The outbox relay boundary, partition key, handler contract, and projection receipts allow a future Kafka transport without changing aggregation or API code.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, `JdbcTemplate`, PostgreSQL 17, Flyway, Micrometer, Testcontainers, React 19, TypeScript 5.8, TanStack Query 5, Vitest 3, k6.

## Global Constraints

- Follow `docs/superpowers/specs/2026-07-17-milestone-31-promotion-and-performance-operations-dashboard-design.md` exactly.
- Preserve M29 durable purchase idempotency, conditional inventory reservation, deterministic cart locking, and exactly-once coupon/stock compensation.
- Do not add Kafka, a message broker dependency, placeholder Kafka classes, an OLAP store, or a general observability platform.
- Use `Asia/Seoul` calendar dates and reject custom dashboard ranges longer than 90 inclusive days.
- OWNER and MANAGER may read their store dashboard; only eligible OWNER actors may mutate campaigns.
- Store-owned campaigns remain read-only to ADMIN actors. ADMIN may mutate only existing platform-owned coupon campaigns.
- Never use dashboard projections to authorize pricing, claims, purchases, inventory changes, or campaign commands.
- Retain processed operational events for at least 100 days; never clean pending, retrying, dead, or active-checkpoint data.
- New JUnit `@Test` method names must be Korean with underscores.
- Run backend Gradle work with JDK 21 and `JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum`.
- Preserve the user's existing modified roadmap/handoff files and untracked M24 plan; stage only files named by the active task.
- Use TDD for every behavior change and commit after every task.

---

### Task 1: Add the M31 persistence schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__add_operations_dashboard_infrastructure.sql`
- Create: `backend/src/test/java/com/sweet/market/store/migration/OperationsDashboardMigrationTest.java`
- Modify: `backend/src/test/java/com/sweet/market/store/migration/StoreFreshDatabaseStartupTest.java`
- Modify: `backend/src/test/java/com/sweet/market/store/migration/StoreSpringBootFlywayTest.java`

**Interfaces:**
- Produces: outbox, generation, receipt, metric, inventory, audit, and performance tables consumed by every later task.
- Produces: Flyway schema version `15`.

- [ ] **Step 1: Write a failing PostgreSQL migration test**

Create `OperationsDashboardMigrationTest` with Testcontainers and assert the exact schema contract:

```java
@Test
void 운영_대시보드_이벤트와_집계_스키마를_생성한다() {
    assertThat(tableExists("operational_event_outbox")).isTrue();
    assertThat(tableExists("projection_generations")).isTrue();
    assertThat(tableExists("projection_event_receipts")).isTrue();
    assertThat(tableExists("store_metric_hourly")).isTrue();
    assertThat(tableExists("campaign_metric_hourly")).isTrue();
    assertThat(tableExists("inventory_pressure_projection")).isTrue();
    assertThat(tableExists("inventory_failure_hourly")).isTrue();
    assertThat(tableExists("campaign_audit_projection")).isTrue();
    assertThat(tableExists("performance_measurement_runs")).isTrue();
    assertThat(tableExists("performance_endpoint_metrics")).isTrue();
    assertThat(tableExists("performance_query_evidence")).isTrue();
    assertThat(indexExists("idx_operational_event_outbox_poll")).isTrue();
    assertThat(indexExists("idx_campaign_metric_hourly_store_bucket")).isTrue();
    assertThat(indexExists("idx_inventory_pressure_store_attention")).isTrue();
    assertThat(uniqueConstraintExists("uq_projection_event_receipts_generation_projection_event")).isTrue();
}
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.migration.OperationsDashboardMigrationTest'
```

Expected: FAIL because the V15 tables do not exist.

- [ ] **Step 3: Add the complete V15 schema**

Create the migration with these concrete table contracts:

```sql
CREATE TABLE projection_generations (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    tracking_started_at TIMESTAMPTZ NOT NULL,
    bootstrap_high_water_id BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    CONSTRAINT chk_projection_generations_status
        CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_projection_generations_active
    ON projection_generations ((status)) WHERE status = 'ACTIVE';

CREATE TABLE operational_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    schema_version INTEGER NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT,
    aggregate_version BIGINT,
    store_id BIGINT,
    campaign_id BIGINT,
    partition_key VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    delivery_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_operational_event_outbox_state
        CHECK (delivery_state IN ('PENDING', 'RETRY', 'PROCESSED', 'DEAD')),
    CONSTRAINT chk_operational_event_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_operational_event_outbox_poll
    ON operational_event_outbox (delivery_state, next_attempt_at, id);
CREATE INDEX idx_operational_event_outbox_cleanup
    ON operational_event_outbox (processed_at) WHERE delivery_state = 'PROCESSED';

CREATE TABLE projection_event_receipts (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    projection_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_projection_event_receipts_generation_projection_event
        UNIQUE (generation_id, projection_name, event_id)
);

CREATE TABLE store_metric_hourly (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    store_id BIGINT NOT NULL,
    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
    order_success_count BIGINT NOT NULL DEFAULT 0,
    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
    reservation_failure_count BIGINT NOT NULL DEFAULT 0,
    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
    sold_out_transition_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (generation_id, bucket_start, store_id, outcome_reason)
);

CREATE INDEX idx_store_metric_hourly_store_bucket
    ON store_metric_hourly (generation_id, store_id, bucket_start);

CREATE TABLE campaign_metric_hourly (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    commerce_store_id BIGINT NOT NULL DEFAULT 0,
    campaign_kind VARCHAR(20) NOT NULL,
    campaign_id BIGINT NOT NULL,
    campaign_owner_type VARCHAR(20) NOT NULL,
    campaign_owner_store_id BIGINT NOT NULL DEFAULT 0,
    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
    claim_success_count BIGINT NOT NULL DEFAULT 0,
    claim_failure_count BIGINT NOT NULL DEFAULT 0,
    redemption_success_count BIGINT NOT NULL DEFAULT 0,
    redemption_failure_count BIGINT NOT NULL DEFAULT 0,
    order_success_count BIGINT NOT NULL DEFAULT 0,
    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (
        generation_id, bucket_start, commerce_store_id, campaign_kind,
        campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
    )
);

CREATE INDEX idx_campaign_metric_hourly_store_bucket
    ON campaign_metric_hourly (generation_id, commerce_store_id, bucket_start, campaign_kind, campaign_id);

CREATE TABLE inventory_pressure_projection (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    sales_policy VARCHAR(20) NOT NULL,
    available_quantity INTEGER,
    low_stock BOOLEAN NOT NULL,
    last_sold_out_at TIMESTAMPTZ,
    recent_reservation_failure_count BIGINT NOT NULL DEFAULT 0,
    last_reservation_failure_at TIMESTAMPTZ,
    aggregate_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (generation_id, product_id)
);

CREATE INDEX idx_inventory_pressure_store_attention
    ON inventory_pressure_projection (
        generation_id, store_id, low_stock DESC,
        recent_reservation_failure_count DESC, product_id DESC
    );

CREATE TABLE inventory_failure_hourly (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    product_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    failure_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (generation_id, bucket_start, product_id)
);

CREATE INDEX idx_inventory_failure_hourly_store_bucket
    ON inventory_failure_hourly (generation_id, store_id, bucket_start, product_id);

CREATE TABLE campaign_audit_projection (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    campaign_kind VARCHAR(20) NOT NULL,
    campaign_id BIGINT NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_store_id BIGINT NOT NULL DEFAULT 0,
    actor_member_id BIGINT NOT NULL,
    command VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    aggregate_version BIGINT,
    before_summary JSONB,
    after_summary JSONB NOT NULL,
    CONSTRAINT uq_campaign_audit_projection_generation_event UNIQUE (generation_id, event_id)
);

CREATE INDEX idx_campaign_audit_projection_owner_time
    ON campaign_audit_projection (generation_id, owner_store_id, occurred_at DESC, event_id DESC);

CREATE TABLE performance_measurement_runs (
    id BIGSERIAL PRIMARY KEY,
    measurement_id UUID NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    git_commit VARCHAR(64) NOT NULL,
    dirty_worktree BOOLEAN NOT NULL,
    fixture_version VARCHAR(80) NOT NULL,
    scenario_version VARCHAR(80) NOT NULL,
    environment_name VARCHAR(80) NOT NULL,
    hardware_description VARCHAR(500) NOT NULL,
    artifact_directory VARCHAR(500) NOT NULL,
    warmup_seconds INTEGER NOT NULL,
    measured_seconds INTEGER NOT NULL,
    off_started_at TIMESTAMPTZ NOT NULL,
    off_completed_at TIMESTAMPTZ NOT NULL,
    on_started_at TIMESTAMPTZ NOT NULL,
    on_completed_at TIMESTAMPTZ NOT NULL,
    registered_by BIGINT NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_performance_measurement_duration CHECK (warmup_seconds > 0 AND measured_seconds > 0)
);

CREATE TABLE performance_endpoint_metrics (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES performance_measurement_runs(id) ON DELETE CASCADE,
    cache_mode VARCHAR(10) NOT NULL,
    endpoint VARCHAR(40) NOT NULL,
    p50_millis NUMERIC(12,3) NOT NULL,
    p95_millis NUMERIC(12,3) NOT NULL,
    throughput_per_second NUMERIC(14,3) NOT NULL,
    error_rate NUMERIC(8,7) NOT NULL,
    jdbc_statement_count BIGINT NOT NULL,
    cache_hit_count BIGINT,
    cache_miss_count BIGINT,
    cache_eviction_count BIGINT,
    CONSTRAINT uq_performance_endpoint_metrics_run_mode_endpoint UNIQUE (run_id, cache_mode, endpoint),
    CONSTRAINT chk_performance_endpoint_metrics_mode CHECK (cache_mode IN ('OFF', 'ON')),
    CONSTRAINT chk_performance_endpoint_metrics_percentiles CHECK (p50_millis >= 0 AND p95_millis >= p50_millis),
    CONSTRAINT chk_performance_endpoint_metrics_error_rate CHECK (error_rate >= 0 AND error_rate <= 1)
);

CREATE TABLE performance_query_evidence (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES performance_measurement_runs(id) ON DELETE CASCADE,
    cache_mode VARCHAR(10) NOT NULL,
    query_shape VARCHAR(40) NOT NULL,
    bind_summary VARCHAR(1000) NOT NULL,
    plan_summary VARCHAR(4000) NOT NULL,
    execution_millis NUMERIC(12,3) NOT NULL,
    actual_rows BIGINT NOT NULL,
    shared_hit_blocks BIGINT NOT NULL,
    shared_read_blocks BIGINT NOT NULL,
    CONSTRAINT uq_performance_query_evidence_run_mode_shape UNIQUE (run_id, cache_mode, query_shape),
    CONSTRAINT chk_performance_query_evidence_mode CHECK (cache_mode IN ('OFF', 'ON')),
    CONSTRAINT chk_performance_query_evidence_values CHECK (
        execution_millis >= 0 AND actual_rows >= 0 AND shared_hit_blocks >= 0 AND shared_read_blocks >= 0
    )
);
```

- [ ] **Step 4: Update Flyway version and fresh-schema assertions**

Change both existing migration tests from version `14` to `15`. Extend `StoreFreshDatabaseStartupTest` with:

```java
assertThat(tableExists("operational_event_outbox")).isTrue();
assertThat(tableExists("campaign_metric_hourly")).isTrue();
assertThat(tableExists("performance_measurement_runs")).isTrue();
assertThat(indexExists("idx_operational_event_outbox_poll")).isTrue();
```

- [ ] **Step 5: Run migration tests**

Run:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.store.migration.OperationsDashboardMigrationTest' --tests 'com.sweet.market.store.migration.StoreFreshDatabaseStartupTest' --tests 'com.sweet.market.store.migration.StoreSpringBootFlywayTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/resources/db/migration/V15__add_operations_dashboard_infrastructure.sql backend/src/test/java/com/sweet/market/store/migration/OperationsDashboardMigrationTest.java backend/src/test/java/com/sweet/market/store/migration/StoreFreshDatabaseStartupTest.java backend/src/test/java/com/sweet/market/store/migration/StoreSpringBootFlywayTest.java
git commit -m "feat: add operations projection schema"
```

### Task 2: Add transport-neutral event recording

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/event/OperationalEvent.java`
- Create: `backend/src/main/java/com/sweet/market/operations/event/OperationalEventType.java`
- Create: `backend/src/main/java/com/sweet/market/operations/event/OperationalEventRecorder.java`
- Create: `backend/src/main/java/com/sweet/market/operations/event/JdbcOperationalEventRecorder.java`
- Create: `backend/src/main/java/com/sweet/market/operations/event/OperationalFailureRecorder.java`
- Create: `backend/src/test/java/com/sweet/market/operations/event/OperationalEventRecorderTest.java`

**Interfaces:**
- Produces: `OperationalEventRecorder.record(OperationalEvent event)` for same-transaction success events.
- Produces: `OperationalFailureRecorder.recordSafely(OperationalEvent event)` for post-rollback outcomes.
- Produces: `OperationalEvent.create(OperationalEventType, String, Long, Long, Long, Long, String, Instant, JsonNode)` with UUID, version, partition key, dimensions, timestamp, and payload.

- [ ] **Step 1: Write failing recorder integration tests**

Add these Korean-named tests with exact assertions:

- `원본_트랜잭션이_롤백되면_outbox도_저장하지_않는다`: save a fixture product and event, throw from the transaction callback, then assert both fixture and event counts are zero.
- `성공_명령과_outbox를_같은_트랜잭션에_저장한다`: commit the same callback and assert every envelope column and JSON payload equals the input.
- `실패_결과는_REQUIRES_NEW로_기록하고_원래_예외를_바꾸지_않는다`: roll back an outer transaction after `recordSafely`, assert the original exception type/message, and assert one committed failure event.

- [ ] **Step 2: Run the tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.event.OperationalEventRecorderTest'
```

Expected: compilation failure because the event contracts do not exist.

- [ ] **Step 3: Add the event contract**

Use these exact public types:

```java
public enum OperationalEventType {
    CAMPAIGN_COMMAND_COMPLETED,
    COUPON_CLAIM_OUTCOME,
    COUPON_REDEMPTION_OUTCOME,
    PURCHASE_OUTCOME,
    ORDER_STATUS_CHANGED,
    INVENTORY_OUTCOME
}

public record OperationalEvent(
        UUID eventId,
        OperationalEventType eventType,
        int schemaVersion,
        String aggregateType,
        Long aggregateId,
        Long aggregateVersion,
        Long storeId,
        Long campaignId,
        String partitionKey,
        Instant occurredAt,
        JsonNode payload
) {
    public static OperationalEvent create(
            OperationalEventType eventType, String aggregateType, Long aggregateId,
            Long aggregateVersion, Long storeId, Long campaignId,
            String partitionKey, Instant occurredAt, JsonNode payload
    ) {
        return new OperationalEvent(UUID.randomUUID(), eventType, 1, aggregateType, aggregateId,
                aggregateVersion, storeId, campaignId, partitionKey, occurredAt, payload);
    }
}

public interface OperationalEventRecorder {
    void record(OperationalEvent event);
}
```

Validate nonblank aggregate/partition values, positive schema version, nonnull event/time/payload, and reject payloads larger than 32 KiB after serialization.

- [ ] **Step 4: Implement JDBC persistence and safe failure recording**

`JdbcOperationalEventRecorder` first obtains PostgreSQL shared transaction advisory lock key `310031`, then inserts every envelope column through `NamedParameterJdbcTemplate`. Shared locks do not serialize ordinary event writers; Task 7 uses the matching exclusive lock only during final generation activation. `OperationalFailureRecorder` must use an injected `TransactionTemplate` configured with `PROPAGATION_REQUIRES_NEW`:

```java
public void recordSafely(OperationalEvent event) {
    try {
        requiresNew.executeWithoutResult(status -> recorder.record(event));
    } catch (RuntimeException exception) {
        missingOutcomeCounter.increment();
        log.error("Failed to record operational outcome eventId={}", event.eventId(), exception);
    }
}
```

- [ ] **Step 5: Run the recorder tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.event.OperationalEventRecorderTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/event backend/src/test/java/com/sweet/market/operations/event
git commit -m "feat: record durable operational events"
```

### Task 3: Implement idempotent projection delivery and recovery

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/projection/OperationalEventHandler.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/OperationalEventEnvelopeRow.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/OperationalProjectionRepository.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/OperationalProjectionCoordinator.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/DbOutboxProjector.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/OperationalEventRetentionScheduler.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/ProjectionHealthResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/OperationsProjectorProperties.java`
- Create: `backend/src/test/java/com/sweet/market/operations/projection/OperationalProjectionCoordinatorTest.java`
- Create: `backend/src/test/java/com/sweet/market/operations/projection/DbOutboxProjectorSchedulingTest.java`
- Modify: `backend/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: `OperationalEvent` and V15 outbox/generation/receipt tables.
- Produces: `OperationalEventHandler.supports(type, schemaVersion)` and `handle(generationId, event)`.
- Produces: `OperationalProjectionCoordinator.projectNextBatch(Instant now, int batchSize)`.
- Produces: `OperationalProjectionRepository.health(Instant now)`.

- [ ] **Step 1: Write failing projector behavior tests**

Add these tests with exact assertions:

- `같은_이벤트를_중복_전달해도_projection을_한번만_갱신한다`: deliver one UUID twice and assert one handler mutation plus one receipt.
- `하나의_event를_지원하는_모든_handler가_성공한뒤에만_완료한다`: register two handlers, fail the second, assert the first handler mutation and receipt are both rolled back and the outbox remains retryable; on retry assert both projections exactly once.
- `일시_실패는_재시도_시각과_횟수를_기록한다`: throw once and assert `RETRY`, attempt `1`, and `nextAttemptAt=now+2s`.
- `열번째_실패는_DEAD로_격리한다`: seed attempt `9`, throw, and assert `DEAD`, attempt `10`, and no further selection.
- `지원하지_않는_schema_version은_즉시_DEAD로_격리한다`: use version `2` against a version-1 handler and assert `DEAD` after one attempt.
- `한_worker가_잠근_event를_다른_worker가_가져가지_않는다`: hold the first PostgreSQL transaction open and assert the second worker selects zero rows.
- `처리완료_100일이전_event만_정리한다`: assert old `PROCESSED` deletion while pending, retry, dead, and 100-day-boundary rows remain.

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.*'
```

Expected: compilation failure for missing projector types.

- [ ] **Step 3: Implement handler and repository contracts**

```java
public interface OperationalEventHandler {
    String projectionName();
    boolean supports(OperationalEventType eventType, int schemaVersion);
    void handle(long generationId, OperationalEvent event);
}

public record ProjectionHealthResponse(
        long pendingCount,
        long retryCount,
        long deadCount,
        Instant oldestUnprocessedAt,
        long projectionLagSeconds,
        Instant projectionUpdatedAt
) { }
```

The repository query must select only `PENDING`/`RETRY` rows with `next_attempt_at <= :now`, ordered by outbox `id`, and append `FOR UPDATE SKIP LOCKED LIMIT :batchSize`.

- [ ] **Step 4: Implement projection transactions and retry policy**

For each selected event, resolve every supporting handler and process them in one transaction:

```java
List<OperationalEventHandler> supportingHandlers = handlers.stream()
        .filter(handler -> handler.supports(event.eventType(), event.schemaVersion()))
        .toList();
if (supportingHandlers.isEmpty()) {
    throw new UnsupportedOperationalEventSchemaException(event.eventType(), event.schemaVersion());
}
for (OperationalEventHandler handler : supportingHandlers) {
    if (!repository.hasReceipt(generationId, handler.projectionName(), event.eventId())) {
        handler.handle(generationId, event);
        repository.insertReceipt(generationId, handler.projectionName(), event.eventId(), now);
    }
}
repository.markProcessed(event.eventId(), now);
```

On transient failure, increment attempts and set delay to `min(2^attemptCount, 300)` seconds. On attempt 10 or unsupported schema, mark `DEAD`. Truncate stored error summaries to 1000 characters and never store a stack trace in the table.

- [ ] **Step 5: Add the scheduled adapter**

`DbOutboxProjector` calls `projectNextBatch(clock.instant(), properties.batchSize())` with:

```yaml
market:
  operations-projector:
    enabled: true
    fixed-delay-ms: 1000
    batch-size: 100
```

Guard scheduling with `@ConditionalOnProperty(prefix = "market.operations-projector", name = "enabled", havingValue = "true", matchIfMissing = true)` and disable it in deterministic integration tests.

`OperationalEventRetentionScheduler` runs daily and deletes only `PROCESSED` rows with `processed_at < now - 100 days`. The repository delete predicate must exclude every other delivery state.

- [ ] **Step 6: Run projector tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/projection backend/src/test/java/com/sweet/market/operations/projection backend/src/main/resources/application.yaml
git commit -m "feat: project operational events idempotently"
```

### Task 4: Audit promotion and coupon campaign commands

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/campaign/CampaignCommandEventFactory.java`
- Create: `backend/src/main/java/com/sweet/market/operations/campaign/CampaignCommandPayload.java`
- Create: `backend/src/main/java/com/sweet/market/operations/campaign/CampaignAuditEventHandler.java`
- Create: `backend/src/test/java/com/sweet/market/operations/campaign/CampaignAuditProjectionTest.java`
- Modify: `backend/src/main/java/com/sweet/market/promotion/application/PromotionCampaignService.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/CouponCampaignService.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/api/AdminCouponCampaignController.java`
- Modify: `backend/src/test/java/com/sweet/market/promotion/PromotionCampaignApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/coupon/CouponCampaignApiTest.java`

**Interfaces:**
- Consumes: same-transaction `OperationalEventRecorder` and projector handler contract.
- Produces: version-1 `CAMPAIGN_COMMAND_COMPLETED` payload.
- Changes: platform coupon mutation service signatures accept `actorMemberId`.

- [ ] **Step 1: Write failing audit tests**

Add these tests and assertions:

- `상점_OWNER의_프로모션_생성과_수정과_수명주기를_감사한다`: execute all six commands and assert command order, owner store, actor ID, and before/after status.
- `플랫폼_쿠폰_명령은_ADMIN_행위자를_감사한다`: assert `ownerType=PLATFORM`, `ownerStoreId=0`, and authenticated admin ID.
- `상점_캠페인을_조회한_ADMIN은_변경_감사를_생성하지_않는다`: perform admin reads and assert no outbox/audit row.
- `감사_payload에_사업자등록번호와_회원_개인정보를_넣지_않는다`: serialize payload and assert the sensitive field names and fixture values are absent.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.campaign.CampaignAuditProjectionTest' --tests 'com.sweet.market.promotion.PromotionCampaignApiTest' --tests 'com.sweet.market.coupon.CouponCampaignApiTest'
```

Expected: FAIL because no audit event is recorded and admin mutations have no actor argument.

- [ ] **Step 3: Define the command payload and bounded summaries**

Use this payload shape:

```java
public record CampaignCommandPayload(
        String campaignKind,
        long campaignId,
        String ownerType,
        Long ownerStoreId,
        long actorMemberId,
        String command,
        JsonNode beforeSummary,
        JsonNode afterSummary
) { }
```

The summary allowlist is `title`, `label`, `scope`, `discountType`, `discountValue`, `priority`, `minimumPurchaseAmount`, `issueLimit`, `startAt`/`issueStartsAt`, `endAt`/`issueEndsAt`, `lifecycleStatus`, and sorted target product IDs. Do not serialize an entity.

- [ ] **Step 4: Record events in campaign transactions**

After each successful `save`/transition, call:

```java
eventRecorder.record(eventFactory.completed(
        campaignKind, campaignId, campaignVersion, ownerType, ownerStoreId,
        actorMemberId, command, beforeSummary, afterSummary, clock.instant()
));
```

Change platform coupon mutations to signatures such as:

```java
public CouponCampaignResponse pausePlatform(Long actorMemberId, Long campaignId)
```

Extract `AuthenticatedMember.id()` in `AdminCouponCampaignController` for create, update, schedule, pause, resume, and end.

- [ ] **Step 5: Project audit rows**

`CampaignAuditEventHandler` inserts one `campaign_audit_projection` row for the active generation. Use the event occurrence, aggregate version, and event ID for deterministic ordering; receipt deduplication prevents duplicates.

- [ ] **Step 6: Run focused tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.campaign.CampaignAuditProjectionTest' --tests 'com.sweet.market.promotion.PromotionCampaignApiTest' --tests 'com.sweet.market.coupon.CouponCampaignApiTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/campaign backend/src/main/java/com/sweet/market/promotion/application/PromotionCampaignService.java backend/src/main/java/com/sweet/market/coupon/application/CouponCampaignService.java backend/src/main/java/com/sweet/market/coupon/api/AdminCouponCampaignController.java backend/src/test/java/com/sweet/market/operations/campaign backend/src/test/java/com/sweet/market/promotion/PromotionCampaignApiTest.java backend/src/test/java/com/sweet/market/coupon/CouponCampaignApiTest.java
git commit -m "feat: audit campaign operations"
```

### Task 5: Record and project coupon outcomes

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/coupon/CouponOutcomeEventFactory.java`
- Create: `backend/src/main/java/com/sweet/market/operations/coupon/CouponOutcomeReason.java`
- Create: `backend/src/main/java/com/sweet/market/operations/coupon/CouponOutcomePayload.java`
- Create: `backend/src/main/java/com/sweet/market/operations/coupon/CouponMetricEventHandler.java`
- Create: `backend/src/test/java/com/sweet/market/operations/coupon/CouponOutcomeProjectionTest.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueService.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueTransactionService.java`
- Modify: `backend/src/main/java/com/sweet/market/coupon/application/CouponRedemptionService.java`
- Modify: `backend/src/test/java/com/sweet/market/coupon/CouponIssueApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/coupon/application/CouponRedemptionServiceTest.java`

**Interfaces:**
- Produces: `COUPON_CLAIM_OUTCOME` and `COUPON_REDEMPTION_OUTCOME` version-1 payloads.
- Produces: campaign-hourly claim/redemption counters by KST hour and normalized reason.

- [ ] **Step 1: Write failing coupon outcome tests**

Add these tests with exact expected counters:

- `새_쿠폰_발급_성공은_원본과_같은_트랜잭션에서_한번_집계한다`: one member coupon, issued count `1`, claim success `1`.
- `이미_발급된_쿠폰_재요청은_ALREADY_CLAIMED로_집계한다`: existing HTTP response preserved, claim failure `1` under `ALREADY_CLAIMED`.
- `발급한도_소진은_EXHAUSTED로_집계한다`: existing error preserved, `EXHAUSTED=1`.
- `비활성_캠페인은_INACTIVE로_집계한다`: existing lifecycle error preserved, `INACTIVE=1`.
- `주문이_커밋되기_전_쿠폰_예약은_사용_성공으로_집계하지_않는다`: reservation exists and redemption success remains `0`.
- `주문과_쿠폰사용이_커밋되면_사용_성공을_한번_집계한다`: used coupon and committed order produce redemption success `1` after duplicate delivery.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.coupon.CouponOutcomeProjectionTest' --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.application.CouponRedemptionServiceTest'
```

Expected: FAIL because outcome events and counters do not exist.

- [ ] **Step 3: Add normalized reasons and payload**

```java
public enum CouponOutcomeReason {
    NONE, ALREADY_CLAIMED, EXHAUSTED, INACTIVE, INELIGIBLE,
    UNAVAILABLE, EXPIRED, SCOPE_MISMATCH, COMBINATION_NOT_ALLOWED,
    RESERVATION_CONFLICT
}

public record CouponOutcomePayload(
        String outcomeType,
        String result,
        CouponOutcomeReason reason,
        long campaignId,
        String ownerType,
        Long ownerStoreId,
        Long commerceStoreId,
        Long orderId,
        long couponDiscountAmount
) { }
```

- [ ] **Step 4: Record claim results at the correct transaction boundary**

Record true issuance success inside `CouponIssueTransactionService` where `MemberCoupon` and `issuedCount` commit. In `CouponIssueService`, record existing-coupon, exhausted, inactive, ineligible, and unavailable outcomes through `OperationalFailureRecorder` after mapping the existing result/exception. Preserve all existing HTTP results and Redis/pessimistic fallback behavior.

- [ ] **Step 5: Record redemption only on durable use**

Do not count `CouponReservation.reserve`. Record successful redemption in the transaction that marks the member coupon used and completes order creation/payment approval. Map rejected reservation/eligibility exceptions to the normalized reasons without changing the exception returned to purchase code.

- [ ] **Step 6: Implement campaign metric upserts**

Truncate event times to KST hour and use PostgreSQL `INSERT` with `ON CONFLICT DO UPDATE` to increment exactly one relevant counter. Store `commerce_store_id=0` for a platform claim before a product/order supplies a commerce store, and store `campaign_owner_store_id=0` for platform ownership.

- [ ] **Step 7: Run focused tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.coupon.CouponOutcomeProjectionTest' --tests 'com.sweet.market.coupon.*' --tests 'com.sweet.market.coupon.application.*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/coupon backend/src/main/java/com/sweet/market/coupon/application backend/src/test/java/com/sweet/market/operations/coupon backend/src/test/java/com/sweet/market/coupon
git commit -m "feat: project coupon outcomes"
```

### Task 6: Record purchase, order, payment, and inventory outcomes

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/purchase/PurchaseOutcomeEventFactory.java`
- Create: `backend/src/main/java/com/sweet/market/operations/purchase/PurchaseOutcomeReason.java`
- Create: `backend/src/main/java/com/sweet/market/operations/purchase/PurchaseOutcomePayload.java`
- Create: `backend/src/main/java/com/sweet/market/operations/purchase/StoreMetricEventHandler.java`
- Create: `backend/src/main/java/com/sweet/market/operations/purchase/CampaignOrderMetricEventHandler.java`
- Create: `backend/src/main/java/com/sweet/market/operations/inventory/InventoryOutcomeEventFactory.java`
- Create: `backend/src/main/java/com/sweet/market/operations/inventory/InventoryOutcomePayload.java`
- Create: `backend/src/main/java/com/sweet/market/operations/inventory/InventoryPressureEventHandler.java`
- Create: `backend/src/main/java/com/sweet/market/operations/inventory/InventoryPressureMaintenanceService.java`
- Create: `backend/src/test/java/com/sweet/market/operations/purchase/PurchaseOutcomeProjectionTest.java`
- Create: `backend/src/test/java/com/sweet/market/operations/inventory/InventoryPressureProjectionTest.java`
- Modify: `backend/src/main/java/com/sweet/market/purchase/application/PurchaseReservationService.java`
- Modify: `backend/src/main/java/com/sweet/market/purchase/application/ProductReservationService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/application/PaymentService.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/application/PaymentApprovalTransactionService.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/application/PaymentFailureCompensationService.java`
- Modify: `backend/src/main/java/com/sweet/market/inventory/application/InventoryService.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`
- Modify: `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`

**Interfaces:**
- Produces: `PURCHASE_OUTCOME`, `ORDER_STATUS_CHANGED`, and `INVENTORY_OUTCOME` payloads.
- Produces: store-hourly applied/realized/reversal metrics and current inventory pressure.

- [ ] **Step 1: Write failing outcome and invariant tests**

Add these tests with exact source and projection assertions:

- `주문_생성은_적용_할인액과_주문수를_한번_집계한다`: one committed order yields order count `1` and exact promotion/coupon snapshots.
- `구매확정은_실현_할인액을_확정시각_버킷에_집계한다`: confirmation yields the same immutable amounts in the confirmation-hour bucket.
- `취소와_환불은_각각_별도_할인액으로_집계한다`: canceled and approved-refund orders increment only their respective reversal columns.
- `품절_경쟁_패배는_SOLD_OUT으로_집계하고_재고를_음수로_만들지_않는다`: existing winner count remains exact and loser reason is `SOLD_OUT`.
- `결제실패_보상은_재고와_쿠폰을_한번만_복구한다`: repeated compensation leaves one release and one failure outcome.
- `STOCK_MANAGED_수량_5이하는_저재고로_표시한다`: quantities `6` and `5` produce false and true.
- `SINGLE_ITEM은_저재고에서_제외하고_품절전환은_기록한다`: low-stock false and sold-out transition count `1`.
- `낮은_version의_재고_event는_최신_projection을_덮어쓰지_않는다`: version `3` remains after version `2`, while failure-hour count still increments once.
- `90일밖_재고실패는_recent_count에서_제외한다`: maintenance sums `inventory_failure_hourly` only from `now-90d` inclusive.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.purchase.PurchaseOutcomeProjectionTest' --tests 'com.sweet.market.operations.inventory.InventoryPressureProjectionTest' --tests 'com.sweet.market.purchase.*' --tests 'com.sweet.market.inventory.*'
```

Expected: FAIL because outcome handlers and events do not exist.

- [ ] **Step 3: Define normalized purchase and inventory payloads**

```java
public enum PurchaseOutcomeReason {
    NONE, SOLD_OUT, PRODUCT_UNAVAILABLE, PAYMENT_FAILED, COUPON_REJECTED
}

public record PurchaseOutcomePayload(
        String result,
        PurchaseOutcomeReason reason,
        Long orderId,
        long storeId,
        long productId,
        Long promotionCampaignId,
        Long couponCampaignId,
        long promotionDiscountAmount,
        long couponDiscountAmount
) { }

public record InventoryOutcomePayload(
        String action,
        long productId,
        long storeId,
        String salesPolicy,
        Integer availableQuantity,
        boolean soldOut
) { }
```

- [ ] **Step 4: Record purchase and order events without widening locks**

Record successful direct/cart order events inside the existing `TransactionTemplate` reservation transaction after `orderRepository.save`. Record race and availability failures only after that transaction rolls back through `OperationalFailureRecorder`. Replayed idempotent purchase requests must not emit a second success or failure event.

Record `ORDER_STATUS_CHANGED` in the existing cancel, refund-completion, and confirmation transactions. Include immutable order discount snapshots and the transition timestamp. Do not add a remote call while holding product, inventory, coupon, or order locks.

- [ ] **Step 5: Record payment and inventory events at compensation boundaries**

Record payment failure after `PaymentFailureCompensationService` successfully completes its REQUIRES_NEW compensation. Record inventory reserve/release/restore/sold-out outcomes in the transaction that inserts the corresponding `InventoryAdjustment`. Use the inventory `@Version` or product version as `aggregateVersion`.

- [ ] **Step 6: Implement projection upserts**

`StoreMetricEventHandler` updates the KST hour bucket for the commerce store. It increments applied values at order creation, realized values at confirmation, and reversal values at cancellation/refund without subtracting earlier buckets.

`CampaignOrderMetricEventHandler` applies the same immutable snapshots to separate promotion and coupon campaign rows. A stacked order may update one promotion row and one coupon row, but store order count remains only in `store_metric_hourly`; never sum campaign-row order counts to produce the store total.

`InventoryPressureEventHandler` applies only `incomingVersion > storedVersion` and computes:

```java
boolean lowStock = salesPolicy.equals("STOCK_MANAGED")
        && availableQuantity != null
        && availableQuantity <= 5;
```

Reservation failure counts increment independently of the current-state version.
Also increment `inventory_failure_hourly` by product and KST hour. `InventoryPressureMaintenanceService` refreshes `recent_reservation_failure_count` from the last 90 days of those buckets, then deletes buckets strictly older than that window. The current attention ordering therefore never becomes a lifetime counter, while retained outbox events still support a full 90-day rebuild.

- [ ] **Step 7: Run focused tests and M29 regression suites**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.purchase.*' --tests 'com.sweet.market.operations.inventory.*' --tests 'com.sweet.market.purchase.*' --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.refund.RefundRequestApiTest' --tests 'com.sweet.market.coupon.CouponRedemptionConcurrencyTest'
```

Expected: `BUILD SUCCESSFUL`; existing concurrency winner counts and compensation assertions remain unchanged.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/purchase backend/src/main/java/com/sweet/market/operations/inventory backend/src/main/java/com/sweet/market/purchase/application/PurchaseReservationService.java backend/src/main/java/com/sweet/market/purchase/application/ProductReservationService.java backend/src/main/java/com/sweet/market/order/application/OrderService.java backend/src/main/java/com/sweet/market/payment/application backend/src/main/java/com/sweet/market/inventory/application/InventoryService.java backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java backend/src/test/java/com/sweet/market/operations backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java
git commit -m "feat: project purchase and inventory outcomes"
```

### Task 7: Bootstrap and rebuild projection generations

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/projection/ProjectionGenerationService.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/ProjectionBootstrapRepository.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/ProjectionBootstrapSnapshot.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/ProjectionRebuildResult.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/ProjectionGenerationCleanupScheduler.java`
- Create: `backend/src/main/java/com/sweet/market/operations/projection/ProjectionGenerationInitializer.java`
- Create: `backend/src/test/java/com/sweet/market/operations/projection/ProjectionGenerationServiceTest.java`

**Interfaces:**
- Consumes: source orders/coupons, retained outbox events, handlers, and generation tables.
- Produces: `ProjectionGenerationService.ensureActiveGeneration(Instant now)`.
- Produces: `ProjectionGenerationService.rebuild(Long actorMemberId, Instant now)`.
- Produces: `ProjectionBootstrapSnapshot(long generationId, Instant cutoff, long outboxHighWaterId)`.

- [ ] **Step 1: Write failing cutoff and generation tests**

Add these tests with exact assertions:

- `초기_bootstrap은_cutoff까지_최근90일_성공사실을_집계한다`: repeatable-read source order/issued-coupon totals match the building generation and store its starting outbox high-water ID.
- `애플리케이션_시작은_ACTIVE_generation이_없을때만_초기화한다`: first run creates one active generation and repeated start leaves the same ID.
- `bootstrap_high_water이후_event를_replay해_배포중_변경을_놓치지_않는다`: one transaction that started before cutoff but committed afterward appears exactly once by outbox ID.
- `사용시각없는_과거쿠폰사용과_실패와_감사를_추정하지_않는다`: redemption/failure/audit remain zero and `trackingStartedAt=cutoff`.
- `재구축중에는_기존_ACTIVE_generation을_계속_조회한다`: active generation ID is unchanged before activation.
- `검증된_generation만_원자적으로_ACTIVE로_전환한다`: new active and previous retired occur in one transaction.
- `재구축_실패는_새_generation만_FAILED로_남긴다`: previous active remains and building becomes failed.
- `7일지난_오래된_RETIRED_generation을_정리한다`: keep active and newest retired, delete older retired rows through cascade.

- [ ] **Step 2: Run the tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.ProjectionGenerationServiceTest'
```

Expected: compilation failure for missing generation service.

- [ ] **Step 3: Implement source bootstrap queries**

In one repeatable-read transaction, capture `cutoff`, the current maximum outbox `id` as `bootstrapHighWaterId`, and source facts. Use half-open UTC instants derived from KST `now.minus(89 days).toLocalDate().atStartOfDay(SEOUL)` through `cutoff`. Bootstrap:

- created orders into applied amounts and order counts by `orderedAt`;
- confirmed orders into realized amounts by `confirmedAt`;
- member-coupon issuance by `issuedAt`; historical redemption is not bootstrapped because `MemberCoupon` has no durable `usedAt`; and
- current product/inventory availability into `inventory_pressure_projection`.

Do not synthesize failure reasons or campaign command history. Set `trackingStartedAt=cutoff`.

- [ ] **Step 4: Implement generation activation**

`rebuild` must:

```java
Instant trackingStartedAt = repository.findActiveTrackingStartedAt().orElse(clock.instant());
BootstrapSnapshot snapshot = bootstrapRepository.createBuildingAndPopulate(
        clock.instant(), trackingStartedAt);
long generationId = snapshot.generationId();
coordinator.replayNonDerivableEvents(generationId, trackingStartedAt, snapshot.outboxHighWaterId());
coordinator.replayAfterOutboxId(generationId, snapshot.outboxHighWaterId());
repository.verifyNoDuplicateReceipts(generationId);
repository.withExclusiveAdvisoryLock(310031L, () -> {
    coordinator.replayAfterCurrentCheckpoint(generationId);
    repository.activateAtomically(generationId, clock.instant());
});
return new ProjectionRebuildResult(generationId, "ACTIVE", snapshot.cutoff(), clock.instant());
```

The exclusive advisory lock briefly blocks new outbox commits after all existing event writers release their shared transaction locks. The final replay and activation therefore have no event gap. On failure mark only the building generation `FAILED`. Activation retires the previous active generation in the same transaction.
The cleanup scheduler keeps the active generation and newest retired generation, then deletes older retired generations whose `retired_at` is more than seven days old. V15 cascade constraints remove their receipts and read-model rows.
`ProjectionGenerationInitializer` is an `ApplicationRunner` that calls `ensureActiveGeneration` once. It is disabled by `market.operations-projector.enabled=false` in tests that construct their own generations.

- [ ] **Step 5: Run generation and projector tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.projection.*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/projection backend/src/test/java/com/sweet/market/operations/projection
git commit -m "feat: rebuild operations projections safely"
```

### Task 8: Add store operations dashboard APIs

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/api/DashboardPeriod.java`
- Create: `backend/src/main/java/com/sweet/market/operations/api/DashboardPeriodResolver.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StoreOperationsDashboardController.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StoreOperationsDashboardQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StoreOperationsDashboardResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/DiscountAmountSummary.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/OutcomeReasonCount.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StoreCampaignMetricResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StoreCouponOutcomeResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StoreInventoryPressureResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StorePurchaseOutcomeResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/store/StoreCampaignAuditResponse.java`
- Create: `backend/src/test/java/com/sweet/market/operations/store/StoreOperationsDashboardApiTest.java`

**Interfaces:**
- Consumes: active projection generation, store/campaign/inventory/audit read models, `StoreAccessService.requireOperator`.
- Produces: store dashboard, campaigns, coupon outcomes, inventory pressure, purchase outcomes, and campaign audits GET contracts under `/api/stores/{storeId}/operations`.

- [ ] **Step 1: Write failing API and authorization tests**

Add these tests with exact HTTP/result assertions:

- `OWNER는_자기_상점_운영_요약을_조회한다`: HTTP 200 and fixture totals.
- `MANAGER는_자기_상점_운영_요약과_드릴다운을_조회한다`: HTTP 200 for all six routes.
- `외부_회원은_상점_운영_지표를_조회할_수_없다`: existing `STORE_ACCESS_DENIED` response.
- `개인상점은_주문지표를_보고_캠페인은_빈상태로_받는다`: nonzero order total and empty campaigns page.
- `플랫폼쿠폰이_상점주문에_적용되면_해당상점_할인액에_포함한다`: exact coupon amount under commerce store.
- `조회기간은_KST_반개구간으로_변환한다`: midnight Seoul converts to exact UTC instants.
- `사용자지정_91일은_거부한다`: structured validation error.
- `드릴다운은_상점범위를_SQL에서_제한하고_결정적으로_페이지한다`: no foreign row and no duplicate ID between pages.

- [ ] **Step 2: Run the API test and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.store.StoreOperationsDashboardApiTest'
```

Expected: HTTP 404 or compilation failure because the controller does not exist.

- [ ] **Step 3: Implement one period resolver**

Expose this contract:

```java
public record DashboardPeriod(
        LocalDate from,
        LocalDate to,
        Instant fromInclusive,
        Instant toExclusive,
        String timezone
) { }

public DashboardPeriod resolve(String preset, LocalDate from, LocalDate to, Instant now)
```

Accept `TODAY`, `LAST_7_DAYS`, `LAST_30_DAYS`, `LAST_90_DAYS`, or both custom dates. Reject mixed preset/custom parameters, reversed dates, missing one custom boundary, and inclusive ranges above 90 days. Always return `timezone="Asia/Seoul"`.

- [ ] **Step 4: Implement the overview response and query**

Return this stable top-level shape:

```java
public record StoreOperationsDashboardResponse(
        Long storeId,
        String storeName,
        DashboardPeriod period,
        Instant generatedAt,
        Instant projectionUpdatedAt,
        long projectionLagSeconds,
        Instant trackingStartedAt,
        long claimSuccessCount,
        long redemptionSuccessCount,
        long orderSuccessCount,
        long purchaseFailureCount,
        DiscountAmountSummary promotionDiscounts,
        DiscountAmountSummary couponDiscounts,
        long lowStockCount,
        long soldOutTransitionCount,
        List<OutcomeReasonCount> leadingFailureReasons
) { }

public record DiscountAmountSummary(long applied, long realized, long canceled, long refunded) { }

public record OutcomeReasonCount(String reason, long count) { }
```

`DiscountAmountSummary` has `applied`, `realized`, `canceled`, and `refunded`. Every sum uses `COALESCE(..., 0)` in SQL. Query the active generation once and reuse its ID for all overview statements.

- [ ] **Step 5: Implement paged drill-down endpoints**

Add controller methods for:

```text
/campaigns?page=0&size=20&campaignKind=&status=
/coupon-outcomes?page=0&size=20&reason=
/inventory-pressure?page=0&size=20&attentionOnly=true
/purchase-outcomes?page=0&size=20&reason=
/campaign-audits?page=0&size=20&campaignKind=&command=
```

Cap `size` at 100. Apply `storeId`, period, generation, filter, and deterministic descending time/ID predicates directly in SQL. Do not load source campaign, order, member-coupon, inventory-adjustment, or target collections.

- [ ] **Step 6: Run API and store authorization tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.store.*' --tests 'com.sweet.market.store.StoreOperationsApiTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/api backend/src/main/java/com/sweet/market/operations/store backend/src/test/java/com/sweet/market/operations/store
git commit -m "feat: expose store operations dashboard"
```

### Task 9: Add administrator operations and projection recovery APIs

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/admin/AdminOperationsDashboardController.java`
- Create: `backend/src/main/java/com/sweet/market/operations/admin/AdminOperationsDashboardQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/operations/admin/AdminOperationsDashboardResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/admin/AdminOperationalEventController.java`
- Create: `backend/src/main/java/com/sweet/market/operations/admin/AdminOperationalEventService.java`
- Create: `backend/src/main/java/com/sweet/market/operations/admin/DeadOperationalEventResponse.java`
- Create: `backend/src/test/java/com/sweet/market/operations/admin/AdminOperationsDashboardApiTest.java`
- Create: `backend/src/test/java/com/sweet/market/operations/admin/AdminOperationalEventApiTest.java`

**Interfaces:**
- Consumes: all projection dimensions, projector health, dead outbox records, and generation rebuild service.
- Produces: administrator dashboard/campaign/outcome/inventory/audit reads, dead-event retry, and projection rebuild APIs.

- [ ] **Step 1: Write failing administrator API tests**

Add these tests with exact assertions:

- `ADMIN은_플랫폼과_상점별_운영요약을_조회한다`: unfiltered totals and filtered store totals reconcile.
- `ADMIN은_상점캠페인을_조회하지만_상점소유자로_변경하지_않는다`: inspect GET succeeds and no store mutation route is exposed.
- `일반회원은_관리자_운영API에_접근할_수_없다`: HTTP 403.
- `ADMIN은_DEAD_event를_조회하고_payload수정없이_재시도한다`: only delivery columns change.
- `재시도된_event는_중복집계하지_않는다`: existing receipt leaves metric unchanged.
- `ADMIN은_projection_재구축을_시작하고_결과를_받는다`: response generation matches new active generation.

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.admin.*'
```

Expected: HTTP 404 for missing administrator operations endpoints.

- [ ] **Step 3: Implement platform overview and filters**

Return totals, discount summaries, leading outcomes, inventory attention, audit counts, and `ProjectionHealthResponse`. Support `from`, `to`/preset, `storeId`, `ownerType`, `campaignKind`, `campaignStatus`, `productId`, and `reason` only where they apply. Paged tables use a maximum size of 100.

Do not call store campaign command services from this controller. Links to existing platform coupon commands remain a web concern.

- [ ] **Step 4: Implement dead-event inspection and retry**

Expose:

```java
public Page<DeadOperationalEventResponse> findDead(int page, int size)
public void retry(UUID eventId, Long actorMemberId, Instant now)
```

`retry` changes only `delivery_state='RETRY'`, `attempt_count=0`, `next_attempt_at=now`, and `last_error=NULL`. It does not update event identity, type, version, dimensions, occurrence time, or payload.

- [ ] **Step 5: Expose explicit projection rebuild**

Add `POST /api/admin/operational-projections/rebuild`. Extract the authenticated admin member ID and call `ProjectionGenerationService.rebuild`. Return generation ID, cutoff, activated time, and final status. Reject a second rebuild while one generation is `BUILDING`.

- [ ] **Step 6: Run administrator tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.admin.*' --tests 'com.sweet.market.coupon.CouponCampaignApiTest'
```

Expected: `BUILD SUCCESSFUL`; platform coupon commands still pass and store commands remain owner-scoped.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/admin backend/src/test/java/com/sweet/market/operations/admin
git commit -m "feat: expose admin operations dashboard"
```

### Task 10: Persist and query validated performance measurements

**Files:**
- Create: `backend/src/main/java/com/sweet/market/operations/performance/PerformanceMeasurementController.java`
- Create: `backend/src/main/java/com/sweet/market/operations/performance/PerformanceMeasurementService.java`
- Create: `backend/src/main/java/com/sweet/market/operations/performance/PerformanceMeasurementRepository.java`
- Create: `backend/src/main/java/com/sweet/market/operations/performance/PerformanceMeasurementRegisterRequest.java`
- Create: `backend/src/main/java/com/sweet/market/operations/performance/CacheModeMeasurementInput.java`
- Create: `backend/src/main/java/com/sweet/market/operations/performance/PerformanceMeasurementResponse.java`
- Create: `backend/src/main/java/com/sweet/market/operations/performance/EndpointMetricInput.java`
- Create: `backend/src/main/java/com/sweet/market/operations/performance/QueryEvidenceInput.java`
- Create: `backend/src/test/java/com/sweet/market/operations/performance/PerformanceMeasurementApiTest.java`

**Interfaces:**
- Produces: authenticated ADMIN register/list/detail measurement APIs.
- Produces: canonical SHA-256 payload hashing and compatible OFF/ON comparison.

- [ ] **Step 1: Write failing registration tests**

Add these tests with exact assertions:

- `ADMIN은_검증된_cache_off_on_측정쌍을_등록한다`: one run, eight endpoint metrics, and eight query rows.
- `같은_measurementId와_hash는_멱등_성공한다`: same run ID and unchanged child counts.
- `같은_measurementId의_다른_payload는_거부한다`: HTTP 409.
- `p50이_p95보다_크면_거부한다`: HTTP 400 field error.
- `음수와_1을_초과한_errorRate를_거부한다`: HTTP 400 for each boundary.
- `commit_fixture_scenario_hardware가_다른_측정쌍은_거부한다`: HTTP 400 comparability error.
- `일반회원은_성능측정을_등록하거나_조회할_수_없다`: HTTP 403.
- `등록API는_프로세스와_SQL을_실행하지_않는다`: inject repository spy, assert only insert/select methods and no process abstraction exists.

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.performance.PerformanceMeasurementApiTest'
```

Expected: HTTP 404 for missing measurement endpoints.

- [ ] **Step 3: Implement the request contract**

```java
public record PerformanceMeasurementRegisterRequest(
        UUID measurementId,
        String artifactDirectory,
        CacheModeMeasurementInput off,
        CacheModeMeasurementInput on
) { }

public record CacheModeMeasurementInput(
        String cacheMode,
        String gitCommit,
        boolean dirtyWorktree,
        String fixtureVersion,
        String scenarioVersion,
        String environmentName,
        String hardwareDescription,
        int warmupSeconds,
        int measuredSeconds,
        Instant startedAt,
        Instant completedAt,
        List<EndpointMetricInput> endpointMetrics,
        List<QueryEvidenceInput> queryEvidence
) { }

public record EndpointMetricInput(
        String cacheMode,
        String endpoint,
        BigDecimal p50Millis,
        BigDecimal p95Millis,
        BigDecimal throughputPerSecond,
        BigDecimal errorRate,
        long jdbcStatementCount,
        Long cacheHitCount,
        Long cacheMissCount,
        Long cacheEvictionCount
) { }
```

Require `artifactDirectory` to be a normalized repository-relative path under `performance/results/` with no `..` segment. Require `off.cacheMode=OFF` and `on.cacheMode=ON`. Require both modes to contain `catalog`, `events`, `popularity`, and `detail`, plus query evidence for `GLOBAL_CATALOG`, `FIXED_STORE_CATALOG`, `ACTIVE_EVENTS`, and `POPULARITY`. Compare commit, dirty declaration, fixture, scenario, environment, hardware, warm-up, and measured duration before persistence. Cap canonical JSON at 1 MiB.

- [ ] **Step 4: Implement validation, hashing, and idempotency**

Serialize the validated record with the application `ObjectMapper`, hash UTF-8 bytes with SHA-256 lowercase hex, then check `measurement_id`. Existing identical hash returns the existing response. Existing different hash throws a structured conflict. Persist run and children in one transaction.

- [ ] **Step 5: Implement list and detail queries**

Expose:

```text
POST /api/admin/performance-measurements
GET  /api/admin/performance-measurements?page=0&size=20
GET  /api/admin/performance-measurements/{runId}
```

The list returns metadata and a validity/comparability summary without all query-plan text. Detail returns endpoint metrics and query evidence. Do not deserialize or execute stored bind/plan text.

- [ ] **Step 6: Run performance API tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.performance.*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/operations/performance backend/src/test/java/com/sweet/market/operations/performance
git commit -m "feat: register catalog performance evidence"
```

### Task 11: Build the reproducible M30 fixture and evidence pipeline

**Files:**
- Create: `backend/src/main/java/com/sweet/market/discovery/experiment/M30PerformanceFixtureInitializer.java`
- Create: `backend/src/main/resources/application-performance-fixture.yaml`
- Create: `backend/src/test/java/com/sweet/market/discovery/M30PerformanceFixtureInitializerTest.java`
- Create: `performance/collect-m30-measurement.ps1`
- Create: `performance/normalize-m30-measurement.mjs`
- Create: `performance/normalize-m30-measurement.test.mjs`
- Create: `performance/fixtures/normalizer-input.json`
- Create: `performance/results/m30-v1/metadata.json`
- Create: `performance/results/m30-v1/k6-off.json`
- Create: `performance/results/m30-v1/k6-on.json`
- Create: `performance/results/m30-v1/metrics-off.json`
- Create: `performance/results/m30-v1/metrics-on.json`
- Create: `performance/results/m30-v1/query-evidence.json`
- Create: `performance/results/m30-v1/measurement.json`
- Modify: `docs/superpowers/reports/2026-07-16-milestone-30-catalog-read-performance.md`

**Interfaces:**
- Consumes: an empty local database, unchanged `performance/m30-catalog-reads.js`, authorized Actuator token, PostgreSQL CLI access, and measurement registration API.
- Produces: deterministic fixture version `m30-v1` and one valid measurement JSON file.

- [ ] **Step 1: Write a failing fixture shape test**

```java
@Test
void 빈_DB에_m30_v1_대표_fixture를_재현한다() {
    initializer.run();
    assertThat(count("stores", "type = 'BUSINESS' AND status = 'ACTIVE'")).isEqualTo(10);
    assertThat(count("products", "1=1")).isEqualTo(10_000);
    assertThat(count("promotion_campaigns", "lifecycle_status = 'SCHEDULED'")).isEqualTo(20);
    assertThat(count("coupon_campaigns", "lifecycle_status = 'SCHEDULED'")).isEqualTo(20);
    assertThat(count("wishlists", "1=1")).isGreaterThanOrEqualTo(50_000);
    assertThat(count("product_view_events", "viewed_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'"))
            .isGreaterThanOrEqualTo(200_000);
}
```

Also assert that running against a nonempty database fails with an explicit message instead of partially adding data.

- [ ] **Step 2: Run the fixture test and verify failure**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.discovery.M30PerformanceFixtureInitializerTest'
```

Expected: compilation failure for the missing initializer.

- [ ] **Step 3: Implement the profile-scoped fixture**

Activate the initializer only under `performance-fixture`. Use fixed random seed `310031`, fixed fixture clock, batches of 500, and existing domain/application factories for stores, campaigns, coupon policy, and inventory where invariants matter. Use focused JDBC batches only for high-volume wishlist and product-view facts after member/product foreign keys exist. Abort unless the core marketplace tables are empty.

Add:

```yaml
spring:
  config:
    activate:
      on-profile: performance-fixture
market:
  performance-fixture:
    version: m30-v1
    random-seed: 310031
```

- [ ] **Step 4: Write and test the normalizer**

`normalize-m30-measurement.mjs` accepts:

```text
--metadata <json>
--off-summary <json>
--on-summary <json>
--off-metrics <json>
--on-metrics <json>
--query-evidence <json>
--out <json>
```

It emits the exact Task 10 request contract, preserves milliseconds and rates, and rejects missing endpoints, modes, or query shapes. Test with:

`metadata.json` contains `measurementId`, `artifactDirectory="performance/results/m30-v1"`, and separate `off`/`on` condition objects with commit, dirty declaration, fixture, scenario, environment, hardware, warm-up, measured duration, and timestamps. The normalizer compares those objects before emitting the request.

```powershell
node --test performance/normalize-m30-measurement.test.mjs
```

Expected: all Node tests pass.

- [ ] **Step 5: Implement the collection script**

`collect-m30-measurement.ps1` must require `-BaseUrl`, `-AdminToken`, `-ProductId`, `-Mode`, and `-OutputDirectory`. It snapshots authorized Actuator metrics before and after:

```powershell
$headers = @{ Authorization = "Bearer $AdminToken" }
Invoke-RestMethod "$BaseUrl/actuator/metrics/discovery.jdbc.statements" -Headers $headers
k6 run --summary-export "$OutputDirectory/k6-$Mode.json" `
  -e BASE_URL=$BaseUrl -e PRODUCT_ID=$ProductId performance/m30-catalog-reads.js
Invoke-RestMethod "$BaseUrl/actuator/metrics/discovery.read.duration" -Headers $headers
```

For cache ON, also capture `cache.gets` and eviction metrics. The script never resets or deletes the database.

- [ ] **Step 6: Run fixture and normalizer tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.discovery.M30PerformanceFixtureInitializerTest'
cd ..
node --test performance/normalize-m30-measurement.test.mjs
```

Expected: Gradle and Node tests pass.

- [ ] **Step 7: Execute the real cache-off/on experiment**

Verify `k6 version` first. If the command is missing on Windows, install the currently discoverable winget package and verify again:

```powershell
winget install --id GrafanaLabs.k6 --exact --accept-package-agreements --accept-source-agreements
k6 version
```

Reset the local database through the documented Docker/local workflow, start with `local,performance-fixture,local-experiment,cache-off`, run the unchanged k6 scenario, restart with `local,local-experiment`, and repeat against the same fixture. Capture exact bound SQL from logs and run full `EXPLAIN (ANALYZE, BUFFERS)` for all four required shapes.

Normalize and register:

```powershell
node performance/normalize-m30-measurement.mjs --metadata performance/results/m30-v1/metadata.json --off-summary performance/results/m30-v1/k6-off.json --on-summary performance/results/m30-v1/k6-on.json --off-metrics performance/results/m30-v1/metrics-off.json --on-metrics performance/results/m30-v1/metrics-on.json --query-evidence performance/results/m30-v1/query-evidence.json --out performance/results/m30-v1/measurement.json
Invoke-RestMethod "$baseUrl/api/admin/performance-measurements" -Method Post -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' -InFile 'performance/results/m30-v1/measurement.json'
```

Expected: registration returns HTTP 200/201 with the external measurement UUID and both modes. Replace every `Not measured` cell in the M30 performance report with the captured value and its conditions; do not commit secrets, tokens, raw visitor identifiers, or machine-specific absolute paths.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/sweet/market/discovery/experiment backend/src/main/resources/application-performance-fixture.yaml backend/src/test/java/com/sweet/market/discovery/M30PerformanceFixtureInitializerTest.java performance/collect-m30-measurement.ps1 performance/normalize-m30-measurement.mjs performance/normalize-m30-measurement.test.mjs performance/fixtures/normalizer-input.json performance/results/m30-v1 docs/superpowers/reports/2026-07-16-milestone-30-catalog-read-performance.md
git commit -m "perf: record catalog read evidence"
```

### Task 12: Build the store operations dashboard web experience

**Files:**
- Create: `web/src/features/operations/storeOperationsDashboardApi.ts`
- Create: `web/src/features/operations/storeOperationsDashboardApi.test.ts`
- Create: `web/src/features/operations/OperationsSummaryCards.tsx`
- Create: `web/src/features/operations/OperationsPeriodControls.tsx`
- Create: `web/src/pages/StoreOperationsDashboardPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/pages/MyStorePage.tsx`
- Modify: `web/src/shared/styles.css`

**Interfaces:**
- Consumes: Task 8 store overview and paged drill-down APIs.
- Produces: `/me/store/dashboard` protected by `RequireAuth`.

- [ ] **Step 1: Write failing TypeScript API tests**

```typescript
it('상점과_기간을_포함해_운영요약을_조회한다', async () => {
  await getStoreOperationsDashboard(7, { preset: 'LAST_30_DAYS' });
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/stores/7/operations/dashboard?preset=LAST_30_DAYS',
    expect.anything(),
  );
});

it('드릴다운은_상점과_필터와_page를_보존한다', async () => {
  await getStoreInventoryPressure(7, {
    preset: 'LAST_7_DAYS', attentionOnly: true, page: 2, size: 20,
  });
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/stores/7/operations/inventory-pressure?preset=LAST_7_DAYS&attentionOnly=true&page=2&size=20',
    expect.anything(),
  );
});
```

- [ ] **Step 2: Run the tests and verify failure**

```powershell
cd web
npm test -- storeOperationsDashboardApi.test.ts
```

Expected: FAIL because the API module does not exist.

- [ ] **Step 3: Implement API types and query keys**

Define `DashboardPeriodPreset`, `DiscountAmountSummary`, `StoreOperationsDashboard`, the five drill-down row types, and a shared `Page<T>`. Query keys must include store ID, preset/custom dates, filters, page, and size. Omit empty query parameters rather than sending `undefined` strings.

- [ ] **Step 4: Implement the dedicated page**

The page must render:

```text
header + store selector + period controls
projection freshness/tracking state
claim/redemption/order/failure summary
promotion and coupon applied/realized/reversal amounts
campaign performance table
inventory attention table
leading outcome reasons
links to existing promotion/coupon/sales/inventory/refund/settlement/report routes
```

Use independent TanStack queries for overview and the active drill-down tab so one table failure does not erase summary cards. Display distinct Korean copy for measured zero, tracking not started, projection delay, and request failure.

- [ ] **Step 5: Enforce role-specific controls**

Use the existing operable-store response. OWNER and MANAGER see the same data. Render campaign management links/actions only when `role === 'OWNER'`, `type === 'BUSINESS'`, and `status === 'ACTIVE'`. The server remains authoritative.

- [ ] **Step 6: Add route, navigation entry, and responsive styles**

Add:

```tsx
<Route path="me/store/dashboard" element={<RequireAuth><StoreOperationsDashboardPage /></RequireAuth>} />
```

Add an `운영 대시보드` link to the existing My Store owner/manager workspace. Use a dense grid on desktop and stacked scan-friendly rows below the existing mobile breakpoint; no horizontal page overflow.

- [ ] **Step 7: Run web tests and production build**

```powershell
npm test -- storeOperationsDashboardApi.test.ts
npm run build
```

Expected: Vitest passes and Vite production build succeeds.

- [ ] **Step 8: Commit**

```powershell
git add web/src/features/operations web/src/pages/StoreOperationsDashboardPage.tsx web/src/app/router.tsx web/src/pages/MyStorePage.tsx web/src/shared/styles.css
git commit -m "feat: add store operations dashboard"
```

### Task 13: Build the administrator operations and performance dashboard

**Files:**
- Create: `web/src/features/operations/adminOperationsDashboardApi.ts`
- Create: `web/src/features/operations/adminOperationsDashboardApi.test.ts`
- Create: `web/src/features/operations/PerformanceMeasurementPanel.tsx`
- Create: `web/src/features/operations/ProjectionHealthPanel.tsx`
- Create: `web/src/pages/AdminOperationsDashboardPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`
- Modify: `web/src/shared/styles.css`

**Interfaces:**
- Consumes: Tasks 9-10 administrator overview, drill-down, dead-event, rebuild, and performance APIs.
- Produces: `/admin/dashboard` protected by `RequireAdmin`.

- [ ] **Step 1: Write failing API tests**

```typescript
it('관리자_운영요약은_기간과_상점필터를_전송한다', async () => {
  await getAdminOperationsDashboard({ preset: 'LAST_30_DAYS', storeId: 7 });
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/admin/operations-dashboard?preset=LAST_30_DAYS&storeId=7',
    expect.anything(),
  );
});

it('성능측정_목록과_상세를_조회한다', async () => {
  await getPerformanceMeasurements(1, 20);
  await getPerformanceMeasurement(33);
  expect(fetchMock).toHaveBeenNthCalledWith(1,
    'http://localhost:8080/api/admin/performance-measurements?page=1&size=20', expect.anything());
  expect(fetchMock).toHaveBeenNthCalledWith(2,
    'http://localhost:8080/api/admin/performance-measurements/33', expect.anything());
});
it('DEAD_event_재시도는_payload를_전송하지_않는다', async () => {
  await retryOperationalEvent('4f8dbfd6-cb42-4a94-9fd3-e6b329f617dc');
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/admin/operational-events/4f8dbfd6-cb42-4a94-9fd3-e6b329f617dc/retry',
    expect.objectContaining({ method: 'POST', body: undefined }),
  );
});
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
npm test -- adminOperationsDashboardApi.test.ts
```

Expected: FAIL because administrator operations API functions do not exist.

- [ ] **Step 3: Implement administrator API contracts**

Define overview/filter types, paged campaign/outcome/inventory/audit/dead-event rows, projection health, measurement list/detail, and mutation functions for retry/rebuild. Query keys must include every server filter and pagination value.

- [ ] **Step 4: Implement the administrator dashboard**

Render compact sections in this order:

```text
period/store/campaign filters
platform totals and discount summaries
campaign and outcome drill-down
inventory pressure
campaign audit
performance measurements
projection health and dead events
```

Link platform coupon rows to `/admin/coupons`. Store-owned campaign rows have inspect navigation only and no lifecycle buttons.

- [ ] **Step 5: Implement performance comparison states**

When there is no registered run, render `성능 측정 전` with the documented collection route. For a comparable run, show OFF and ON raw values and computed deltas for p50, p95, throughput, error rate, JDBC statements, and cache statistics. If metadata differs, show both runs independently and omit improvement language.

- [ ] **Step 6: Implement projector operations safely**

Show pending/retry/dead counts, oldest event, lag, and last update. Dead retry requires explicit confirmation and invalidates health/dead queries after success. Rebuild requires explicit confirmation, disables while pending, and never accepts generation IDs or payload data from the browser.

- [ ] **Step 7: Add route, admin navigation, and responsive styles**

```tsx
<Route path="admin/dashboard" element={<RequireAdmin><AdminOperationsDashboardPage /></RequireAdmin>} />
```

Add `운영 대시보드` to the ADMIN navigation. Paginated operational tables collapse nonessential columns into labeled mobile rows while preserving status, time, and primary action.

- [ ] **Step 8: Run web tests and production build**

```powershell
npm test -- adminOperationsDashboardApi.test.ts storeOperationsDashboardApi.test.ts
npm run build
```

Expected: Vitest passes and production build succeeds.

- [ ] **Step 9: Commit**

```powershell
git add web/src/features/operations web/src/pages/AdminOperationsDashboardPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx web/src/shared/styles.css
git commit -m "feat: add admin operations dashboard"
```

### Task 14: Reconcile totals, run full verification, and hand off M31

**Files:**
- Create: `backend/src/test/java/com/sweet/market/operations/OperationsDashboardReconciliationTest.java`
- Create: `docs/superpowers/reports/2026-07-17-milestone-31-operations-dashboard-verification.md`
- Create: `docs/superpowers/handoffs/2026-07-17-milestone-31-promotion-and-performance-operations-dashboard-handoff.md`
- Create: `docs/superpowers/handoffs/2026-07-17-post-milestone-31-next-session-handoff.md`

**Interfaces:**
- Consumes: all M31 code, registered real measurement, and the approved design.
- Produces: final reconciliation evidence and next-session handoff.

- [ ] **Step 1: Write the end-to-end reconciliation test**

Build fixed KST fixtures containing store and platform coupons, promotion+coupon stacking, confirmed, canceled, refunded, sold-out-race, and payment-failure outcomes. Assert:

```java
@Test
void projection_합계가_원본_fixture와_정확히_일치한다() {
    assertThat(storeOverview.orderSuccessCount()).isEqualTo(sourceCreatedOrders);
    assertThat(storeOverview.promotionDiscounts().applied()).isEqualTo(sourcePromotionApplied);
    assertThat(storeOverview.promotionDiscounts().realized()).isEqualTo(sourcePromotionConfirmed);
    assertThat(storeOverview.couponDiscounts().canceled()).isEqualTo(sourceCouponCanceled);
    assertThat(storeOverview.couponDiscounts().refunded()).isEqualTo(sourceCouponRefunded);
    assertThat(storeOverview.purchaseFailureCount()).isEqualTo(recordedPurchaseFailures);
}
```

Also assert store isolation, platform coupon attribution to the commerce store, no double count after replay, and `trackingStartedAt` semantics.

- [ ] **Step 2: Run the reconciliation and focused M31 suites**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.operations.*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the complete backend suite**

```powershell
.\gradlew.bat --no-daemon test
```

Expected: `BUILD SUCCESSFUL` with no M21-M30 regression.

- [ ] **Step 4: Run complete web verification**

```powershell
cd ..\web
npm test
npm run build
```

Expected: all Vitest suites pass and Vite production build succeeds.

- [ ] **Step 5: Run repository hygiene checks**

```powershell
cd ..
git diff --check
git status --short --branch --untracked-files=all
```

Expected: no whitespace errors. Only intentional M31 changes plus the user's pre-existing roadmap/handoff/M24-plan changes appear; do not stage the latter.

- [ ] **Step 6: Perform manual role and measurement flows**

Verify:

```text
OWNER: own store overview, filters, drill-down, owner-only campaign links
MANAGER: same scoped evidence, no campaign mutation controls
OUTSIDER: store operations API denied
ADMIN: cross-store overview, platform coupon links, no store mutation
ADMIN: dead-event retry and projection rebuild confirmation
ADMIN: registered cache OFF/ON run and query evidence visible
MOBILE: both dashboard routes have no horizontal page overflow
```

- [ ] **Step 7: Write verification and handoff documents**

The verification report records exact command results, test counts, build duration, fixture version, measurement UUID, cache-off/on values, SQL plan artifact paths, projection query observations, and remaining limitations. The M31 handoff records delivered boundaries and preserved invariants. The post-M31 handoff states that the M21-M31 roadmap is complete and lists only evidence-backed deferred candidates.

- [ ] **Step 8: Commit final verification and handoff**

```powershell
git add backend/src/test/java/com/sweet/market/operations/OperationsDashboardReconciliationTest.java docs/superpowers/reports/2026-07-17-milestone-31-operations-dashboard-verification.md docs/superpowers/handoffs/2026-07-17-milestone-31-promotion-and-performance-operations-dashboard-handoff.md docs/superpowers/handoffs/2026-07-17-post-milestone-31-next-session-handoff.md
git commit -m "docs: verify and hand off milestone 31"
```

---

## Final Execution Notes

- Execute in an isolated `codex/` worktree created with `superpowers:using-git-worktrees`; do not implement directly in the dirty primary worktree.
- Review each task before starting the next. Tasks 2-7 define correctness infrastructure; do not begin dashboard API or web work while their focused suites are red.
- Do not claim M30 performance improvement until Task 11 has produced and registered real cache-off/on evidence under identical conditions.
- If hourly projection queries miss the measured interaction budget, stop and amend the approved design before adding a daily rollup. Do not silently expand the schema.
