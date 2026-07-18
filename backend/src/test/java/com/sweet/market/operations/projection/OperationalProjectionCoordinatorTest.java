package com.sweet.market.operations.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class OperationalProjectionCoordinatorTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-17T02:03:04Z");

    @Autowired
    private OperationalProjectionCoordinator coordinator;

    @Autowired
    private OperationalProjectionRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private FirstTestProjectionHandler firstHandler;

    @Autowired
    private SecondTestProjectionHandler secondHandler;

    @MockitoBean(name = "storeMetricEventHandler")
    private OperationalEventHandler storeMetricEventHandler;

    @MockitoBean(name = "campaignOrderMetricEventHandler")
    private OperationalEventHandler campaignOrderMetricEventHandler;

    private long generationId;

    @BeforeEach
    void prepareProjectionTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_generations (
                    id BIGSERIAL PRIMARY KEY,
                    status VARCHAR(20) NOT NULL,
                    cutoff_at TIMESTAMPTZ NOT NULL,
                    tracking_started_at TIMESTAMPTZ NOT NULL,
                    bootstrap_high_water_id BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    activated_at TIMESTAMPTZ,
                    retired_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS operational_event_outbox (
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
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_event_receipts (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    projection_name VARCHAR(80) NOT NULL,
                    event_id UUID NOT NULL,
                    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_projection_test_receipt UNIQUE (generation_id, projection_name, event_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_test_mutations (
                    generation_id BIGINT NOT NULL,
                    projection_name VARCHAR(80) NOT NULL,
                    event_id UUID NOT NULL,
                    CONSTRAINT uq_projection_test_mutation UNIQUE (generation_id, projection_name, event_id)
                )
                """);
        jdbcTemplate.execute("""
                TRUNCATE TABLE projection_test_mutations, projection_event_receipts,
                               operational_event_outbox, projection_generations RESTART IDENTITY CASCADE
                """);
        generationId = jdbcTemplate.queryForObject("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, activated_at
                ) VALUES ('ACTIVE', ?, ?, ?)
                RETURNING id
                """, Long.class, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
        firstHandler.reset();
        secondHandler.reset();
    }

    @Test
    void 같은_이벤트를_중복_전달해도_projection을_한번만_갱신한다() {
        UUID eventId = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PENDING", NOW);

        assertThat(coordinator.projectNextBatch(NOW, 100)).isOne();
        jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = 'RETRY', next_attempt_at = ?
                WHERE event_id = ?
                """, Timestamp.from(NOW), eventId);
        assertThat(coordinator.projectNextBatch(NOW, 100)).isOne();

        assertThat(mutationCount("first-projection", eventId)).isOne();
        assertThat(receiptCount(eventId)).isOne();
        assertThat(delivery(eventId).get("delivery_state")).isEqualTo("PROCESSED");
    }

    @Test
    void projection_batch_size는_100을_초과할수없다() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                coordinator.projectNextBatch(NOW, 101));
    }

    @Test
    void 하나의_event를_지원하는_모든_handler가_성공한뒤에만_완료한다() {
        secondHandler.failNext(1);
        UUID eventId = insertEvent(OperationalEventType.ORDER_STATUS_CHANGED, 1, 0, "PENDING", NOW);

        assertThat(coordinator.projectNextBatch(NOW, 100)).isOne();

        assertThat(mutationCount("first-projection", eventId)).isZero();
        assertThat(receiptCount(eventId)).isZero();
        assertThat(delivery(eventId).get("delivery_state")).isEqualTo("RETRY");

        assertThat(coordinator.projectNextBatch(NOW.plusSeconds(2), 100)).isOne();

        assertThat(mutationCount("first-projection", eventId)).isOne();
        assertThat(mutationCount("second-projection", eventId)).isOne();
        assertThat(receiptCount(eventId)).isEqualTo(2);
        assertThat(delivery(eventId).get("delivery_state")).isEqualTo("PROCESSED");
    }

    @Test
    void 일시_실패는_재시도_시각과_횟수를_기록한다() {
        firstHandler.failNext(1);
        UUID eventId = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PENDING", NOW);

        assertThat(coordinator.projectNextBatch(NOW, 100)).isOne();

        Map<String, Object> delivery = delivery(eventId);
        assertThat(delivery.get("delivery_state")).isEqualTo("RETRY");
        assertThat(delivery.get("attempt_count")).isEqualTo(1);
        assertThat(((Timestamp) delivery.get("next_attempt_at")).toInstant()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void 열번째_실패는_DEAD로_격리한다() {
        firstHandler.failNext(1);
        UUID eventId = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 9, "RETRY", NOW);

        assertThat(coordinator.projectNextBatch(NOW, 100)).isOne();

        Map<String, Object> delivery = delivery(eventId);
        assertThat(delivery.get("delivery_state")).isEqualTo("DEAD");
        assertThat(delivery.get("attempt_count")).isEqualTo(10);
        assertThat(repository.lockNextBatch(NOW.plus(1, ChronoUnit.DAYS), 100)).isEmpty();
    }

    @Test
    void 지원하지_않는_schema_version은_즉시_DEAD로_격리한다() {
        UUID eventId = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 2, 0, "PENDING", NOW);

        assertThat(coordinator.projectNextBatch(NOW, 100)).isOne();

        Map<String, Object> delivery = delivery(eventId);
        assertThat(delivery.get("delivery_state")).isEqualTo("DEAD");
        assertThat(delivery.get("attempt_count")).isEqualTo(1);
        assertThat(receiptCount(eventId)).isZero();
    }

    @Test
    void 알수없는_event_type은_DEAD로_격리하고_다음_event는_계속_처리한다() {
        UUID unknownEventId = insertEvent("FUTURE_OPERATIONAL_EVENT", 1, 0, "PENDING", NOW);
        UUID validEventId = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PENDING", NOW);

        assertThat(coordinator.projectNextBatch(NOW, 100)).isEqualTo(2);

        Map<String, Object> unknownDelivery = delivery(unknownEventId);
        assertThat(unknownDelivery.get("delivery_state")).isEqualTo("DEAD");
        assertThat(unknownDelivery.get("attempt_count")).isEqualTo(1);
        assertThat(unknownDelivery.get("last_error")).asString()
                .contains("UnsupportedOperationalEventSchemaException");
        assertThat(receiptCount(unknownEventId)).isZero();
        assertThat(mutationCount("first-projection", unknownEventId)).isZero();
        assertThat(mutationCount("second-projection", unknownEventId)).isZero();
        assertThat(eventType(unknownEventId)).isEqualTo("FUTURE_OPERATIONAL_EVENT");

        assertThat(delivery(validEventId).get("delivery_state")).isEqualTo("PROCESSED");
        assertThat(receiptCount(validEventId)).isOne();
        assertThat(mutationCount("first-projection", validEventId)).isOne();
    }

    @Test
    void 실패_요약은_1000자로_자르고_stack_trace를_저장하지_않는다() {
        firstHandler.failNext(1, "x".repeat(1_200) + "\n\tat example.Stack.trace(Stack.java:1)");
        UUID eventId = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PENDING", NOW);

        coordinator.projectNextBatch(NOW, 100);

        String lastError = (String) delivery(eventId).get("last_error");
        assertThat(lastError).hasSize(1_000);
        assertThat(lastError).doesNotContain("\n\tat ");
    }

    @Test
    void 한_worker가_잠근_event를_다른_worker가_가져가지_않는다() throws Exception {
        insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PENDING", NOW);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<OperationalEventEnvelopeRow>> firstWorker = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        List<OperationalEventEnvelopeRow> rows = repository.lockNextBatch(NOW, 1);
                        locked.countDown();
                        await(release);
                        return rows;
                    }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<List<OperationalEventEnvelopeRow>> secondWorker = executor.submit(() ->
                    transactionTemplate.execute(status -> repository.lockNextBatch(NOW, 1)));

            assertThat(secondWorker.get(5, TimeUnit.SECONDS)).isEmpty();
            release.countDown();
            assertThat(firstWorker.get(5, TimeUnit.SECONDS)).hasSize(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 처리완료_100일이전_event만_정리한다() {
        UUID oldProcessed = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PROCESSED",
                NOW.minus(101, ChronoUnit.DAYS));
        UUID boundaryProcessed = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PROCESSED",
                NOW.minus(100, ChronoUnit.DAYS));
        UUID pending = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PENDING",
                NOW.minus(101, ChronoUnit.DAYS));
        UUID retry = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 1, "RETRY",
                NOW.minus(101, ChronoUnit.DAYS));
        UUID dead = insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 10, "DEAD",
                NOW.minus(101, ChronoUnit.DAYS));

        assertThat(repository.deleteProcessedBefore(NOW.minus(100, ChronoUnit.DAYS))).isOne();

        assertThat(existingEventIds()).containsExactlyInAnyOrder(boundaryProcessed, pending, retry, dead);
        assertThat(existingEventIds()).doesNotContain(oldProcessed);
    }

    @Test
    void projection_건강지표를_집계한다() {
        insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PENDING", NOW.minusSeconds(30));
        insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 1, "RETRY", NOW.minusSeconds(20));
        insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 10, "DEAD", NOW.minusSeconds(10));
        insertEvent(OperationalEventType.PURCHASE_OUTCOME, 1, 0, "PROCESSED", NOW.minusSeconds(5));

        ProjectionHealthResponse health = repository.health(NOW);

        assertThat(health.pendingCount()).isOne();
        assertThat(health.retryCount()).isOne();
        assertThat(health.deadCount()).isOne();
        assertThat(health.oldestUnprocessedAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(health.projectionLagSeconds()).isEqualTo(30);
        assertThat(health.projectionUpdatedAt()).isEqualTo(NOW.minusSeconds(5));
    }

    private UUID insertEvent(
            OperationalEventType type, int schemaVersion, int attemptCount, String state, Instant timestamp
    ) {
        return insertEvent(type.name(), schemaVersion, attemptCount, state, timestamp);
    }

    private UUID insertEvent(
            String type, int schemaVersion, int attemptCount, String state, Instant timestamp
    ) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO operational_event_outbox (
                    event_id, event_type, schema_version, aggregate_type, aggregate_id,
                    aggregate_version, store_id, campaign_id, partition_key, occurred_at,
                    payload, delivery_state, attempt_count, next_attempt_at, processed_at, created_at
                ) VALUES (?, ?, ?, 'purchase', 101, 3, 11, 21, 'purchase:101', ?,
                          CAST(? AS JSONB), ?, ?, ?, ?, ?)
                """,
                eventId, type, schemaVersion, Timestamp.from(timestamp),
                payload().toString(), state, attemptCount, Timestamp.from(NOW),
                "PROCESSED".equals(state) ? Timestamp.from(timestamp) : null,
                Timestamp.from(timestamp));
        return eventId;
    }

    private String eventType(UUID eventId) {
        return jdbcTemplate.queryForObject("""
                SELECT event_type FROM operational_event_outbox WHERE event_id = ?
                """, String.class, eventId);
    }

    private JsonNode payload() {
        return objectMapper.createObjectNode().put("outcome", "SUCCESS").put("amount", 12_000L);
    }

    private Map<String, Object> delivery(UUID eventId) {
        return jdbcTemplate.queryForMap("""
                SELECT delivery_state, attempt_count, next_attempt_at, last_error
                FROM operational_event_outbox
                WHERE event_id = ?
                """, eventId);
    }

    private long mutationCount(String projectionName, UUID eventId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_test_mutations
                WHERE generation_id = ? AND projection_name = ? AND event_id = ?
                """, Long.class, generationId, projectionName, eventId);
    }

    private long receiptCount(UUID eventId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM projection_event_receipts
                WHERE generation_id = ? AND event_id = ?
                """, Long.class, generationId, eventId);
    }

    private List<UUID> existingEventIds() {
        return jdbcTemplate.queryForList(
                "SELECT event_id FROM operational_event_outbox ORDER BY event_id", UUID.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for projection test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for projection test latch", exception);
        }
    }

    @TestConfiguration
    static class HandlerConfiguration {

        @Bean
        FirstTestProjectionHandler firstTestProjectionHandler(JdbcTemplate jdbcTemplate) {
            return new FirstTestProjectionHandler(jdbcTemplate);
        }

        @Bean
        SecondTestProjectionHandler secondTestProjectionHandler(JdbcTemplate jdbcTemplate) {
            return new SecondTestProjectionHandler(jdbcTemplate);
        }
    }

    static final class FirstTestProjectionHandler extends FailableTestProjectionHandler {

        private FirstTestProjectionHandler(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate, "first-projection");
        }

        @Override
        public boolean supports(OperationalEventType eventType, int schemaVersion) {
            return schemaVersion == 1 && (eventType == OperationalEventType.PURCHASE_OUTCOME
                    || eventType == OperationalEventType.ORDER_STATUS_CHANGED);
        }
    }

    static final class SecondTestProjectionHandler extends FailableTestProjectionHandler {

        private SecondTestProjectionHandler(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate, "second-projection");
        }

        @Override
        public boolean supports(OperationalEventType eventType, int schemaVersion) {
            return schemaVersion == 1 && eventType == OperationalEventType.ORDER_STATUS_CHANGED;
        }
    }

    abstract static class FailableTestProjectionHandler implements OperationalEventHandler {

        private final JdbcTemplate jdbcTemplate;
        private final String projectionName;
        private final AtomicInteger failuresRemaining = new AtomicInteger();
        private String failureMessage = "temporary projection failure";

        private FailableTestProjectionHandler(JdbcTemplate jdbcTemplate, String projectionName) {
            this.jdbcTemplate = jdbcTemplate;
            this.projectionName = projectionName;
        }

        @Override
        public String projectionName() {
            return projectionName;
        }

        @Override
        public void handle(long generationId, OperationalEvent event) {
            if (failuresRemaining.getAndUpdate(current -> Math.max(0, current - 1)) > 0) {
                throw new TestProjectionFailure(failureMessage);
            }
            jdbcTemplate.update("""
                    INSERT INTO projection_test_mutations (generation_id, projection_name, event_id)
                    VALUES (?, ?, ?)
                    """, generationId, projectionName, event.eventId());
        }

        void failNext(int count) {
            failuresRemaining.set(count);
        }

        void failNext(int count, String message) {
            failureMessage = message;
            failuresRemaining.set(count);
        }

        void reset() {
            failuresRemaining.set(0);
            failureMessage = "temporary projection failure";
        }
    }

    static final class TestProjectionFailure extends RuntimeException {

        private TestProjectionFailure(String message) {
            super(message);
        }
    }
}
