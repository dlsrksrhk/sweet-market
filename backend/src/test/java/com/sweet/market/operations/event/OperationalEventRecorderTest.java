package com.sweet.market.operations.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.domain.Store;
import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalEventRecorderTest extends IntegrationTestSupport {

    @Autowired
    private OperationalEventRecorder recorder;

    @Autowired
    private OperationalFailureRecorder failureRecorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void prepareOutbox() {
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
        jdbcTemplate.execute("TRUNCATE TABLE operational_event_outbox RESTART IDENTITY");
    }

    @Test
    void 원본_트랜잭션이_롤백되면_outbox도_저장하지_않는다() {
        OperationalEvent event = event(OperationalEventType.PURCHASE_OUTCOME);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            saveFixtureProduct();
            recorder.record(event);
            throw new FixtureFailure("rollback");
        }))
                .isInstanceOf(FixtureFailure.class)
                .hasMessage("rollback");

        assertThat(count("products")).isZero();
        assertThat(count("operational_event_outbox")).isZero();
    }

    @Test
    void 성공_명령과_outbox를_같은_트랜잭션에_저장한다() throws Exception {
        OperationalEvent event = event(OperationalEventType.CAMPAIGN_COMMAND_COMPLETED);

        transactionTemplate.executeWithoutResult(status -> {
            saveFixtureProduct();
            recorder.record(event);
        });

        assertThat(count("products")).isOne();
        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT event_id, event_type, schema_version, aggregate_type, aggregate_id,
                       aggregate_version, store_id, campaign_id, partition_key, occurred_at,
                       payload::text AS payload, delivery_state, attempt_count
                FROM operational_event_outbox
                """);
        assertThat(stored.get("event_id")).isEqualTo(event.eventId());
        assertThat(stored.get("event_type")).isEqualTo(event.eventType().name());
        assertThat(stored.get("schema_version")).isEqualTo(event.schemaVersion());
        assertThat(stored.get("aggregate_type")).isEqualTo(event.aggregateType());
        assertThat(stored.get("aggregate_id")).isEqualTo(event.aggregateId());
        assertThat(stored.get("aggregate_version")).isEqualTo(event.aggregateVersion());
        assertThat(stored.get("store_id")).isEqualTo(event.storeId());
        assertThat(stored.get("campaign_id")).isEqualTo(event.campaignId());
        assertThat(stored.get("partition_key")).isEqualTo(event.partitionKey());
        assertThat(((Timestamp) stored.get("occurred_at")).toInstant()).isEqualTo(event.occurredAt());
        JsonNode storedPayload = objectMapper.reader()
                .with(DeserializationFeature.USE_LONG_FOR_INTS)
                .readTree((String) stored.get("payload"));
        assertThat(storedPayload.equals(event.payload())).isTrue();
        assertThat(stored.get("delivery_state")).isEqualTo("PENDING");
        assertThat(stored.get("attempt_count")).isEqualTo(0);
    }

    @Test
    void UNKNOWN_event_type은_outbox에_기록하지_않는다() {
        OperationalEvent event = new OperationalEvent(
                UUID.randomUUID(),
                OperationalEventType.UNKNOWN,
                1,
                "purchase",
                101L,
                3L,
                11L,
                21L,
                "purchase:101",
                Instant.parse("2026-07-17T01:02:03Z"),
                objectMapper.createObjectNode()
        );

        assertThatThrownBy(() -> recorder.record(event))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("UNKNOWN operational event type must not be recorded");

        assertThat(count("operational_event_outbox")).isZero();
    }

    @Test
    void 실패_결과는_REQUIRES_NEW로_기록하고_원래_예외를_바꾸지_않는다() {
        OperationalEvent event = event(OperationalEventType.INVENTORY_OUTCOME);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            saveFixtureProduct();
            failureRecorder.recordSafely(event);
            throw new FixtureFailure("original failure");
        }))
                .isInstanceOf(FixtureFailure.class)
                .hasMessage("original failure");

        assertThat(count("products")).isZero();
        assertThat(count("operational_event_outbox")).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_id FROM operational_event_outbox", java.util.UUID.class
        )).isEqualTo(event.eventId());
    }

    @Test
    void 이벤트_봉투의_필수값을_검증한다() {
        JsonNode payload = objectMapper.createObjectNode();
        Instant occurredAt = Instant.parse("2026-07-17T01:02:03Z");

        assertThatNullPointerException().isThrownBy(() -> new OperationalEvent(
                null, OperationalEventType.PURCHASE_OUTCOME, 1, "purchase", 101L, 3L,
                11L, 21L, "purchase:101", occurredAt, payload
        ));
        assertThatNullPointerException().isThrownBy(() -> new OperationalEvent(
                java.util.UUID.randomUUID(), null, 1, "purchase", 101L, 3L,
                11L, 21L, "purchase:101", occurredAt, payload
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new OperationalEvent(
                java.util.UUID.randomUUID(), OperationalEventType.PURCHASE_OUTCOME, 0, "purchase", 101L, 3L,
                11L, 21L, "purchase:101", occurredAt, payload
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new OperationalEvent(
                java.util.UUID.randomUUID(), OperationalEventType.PURCHASE_OUTCOME, 1, " ", 101L, 3L,
                11L, 21L, "purchase:101", occurredAt, payload
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new OperationalEvent(
                java.util.UUID.randomUUID(), OperationalEventType.PURCHASE_OUTCOME, 1, "purchase", 101L, 3L,
                11L, 21L, " ", occurredAt, payload
        ));
        assertThatNullPointerException().isThrownBy(() -> new OperationalEvent(
                java.util.UUID.randomUUID(), OperationalEventType.PURCHASE_OUTCOME, 1, "purchase", 101L, 3L,
                11L, 21L, "purchase:101", null, payload
        ));
        assertThatNullPointerException().isThrownBy(() -> new OperationalEvent(
                java.util.UUID.randomUUID(), OperationalEventType.PURCHASE_OUTCOME, 1, "purchase", 101L, 3L,
                11L, 21L, "purchase:101", occurredAt, null
        ));
    }

    @Test
    void 직렬화된_페이로드가_32KiB를_넘으면_거부한다() {
        int oversizedTextLength = 32 * 1024;
        String oversizedText = "가".repeat(oversizedTextLength);
        assertThat(oversizedText.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(32 * 1024);

        assertThatIllegalArgumentException().isThrownBy(() -> OperationalEvent.create(
                OperationalEventType.PURCHASE_OUTCOME, "purchase", 101L, 3L, 11L, 21L,
                "purchase:101", Instant.parse("2026-07-17T01:02:03Z"),
                objectMapper.createObjectNode().put("value", oversizedText)
        ));
    }

    private OperationalEvent event(OperationalEventType eventType) {
        return OperationalEvent.create(
                eventType,
                "purchase",
                101L,
                3L,
                11L,
                21L,
                "purchase:101",
                Instant.parse("2026-07-17T01:02:03Z"),
                objectMapper.createObjectNode()
                        .put("outcome", "SUCCESS")
                        .put("amount", 12_000L)
        );
    }

    private void saveFixtureProduct() {
        Member member = Member.create("operations-fixture@example.com", "encoded-password", "운영 fixture");
        Store store = Store.createPersonal(member, "운영 fixture 상점", "");
        Product product = Product.create(store, "운영 fixture 상품", "테스트 상품", 12_000L);
        entityManager.persist(member);
        entityManager.persist(product);
        entityManager.flush();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static final class FixtureFailure extends RuntimeException {

        private FixtureFailure(String message) {
            super(message);
        }
    }
}
