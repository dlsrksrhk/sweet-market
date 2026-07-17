package com.sweet.market.operations.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class OperationalProjectionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public OperationalProjectionRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public OptionalLong findActiveGenerationId() {
        List<Long> generationIds = jdbcTemplate.queryForList("""
                SELECT id
                FROM projection_generations
                WHERE status = 'ACTIVE'
                """, Map.of(), Long.class);
        if (generationIds.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(generationIds.getFirst());
    }

    public Optional<Instant> findActiveTrackingStartedAt() {
        List<Instant> trackingStartedAt = jdbcTemplate.query("""
                SELECT tracking_started_at
                FROM projection_generations
                WHERE status = 'ACTIVE'
                """, Map.of(), (resultSet, rowNumber) ->
                resultSet.getTimestamp("tracking_started_at").toInstant());
        return trackingStartedAt.stream().findFirst();
    }

    public long findBootstrapHighWaterId(long generationId) {
        Long highWaterId = jdbcTemplate.queryForObject("""
                SELECT bootstrap_high_water_id
                FROM projection_generations
                WHERE id = :generationId
                """, Map.of("generationId", generationId), Long.class);
        if (highWaterId == null) {
            throw new IllegalStateException("Projection generation not found: " + generationId);
        }
        return highWaterId;
    }

    public List<OperationalEventEnvelopeRow> findNonDerivableEvents(
            Instant trackingStartedAt,
            long throughOutboxId
    ) {
        return jdbcTemplate.query("""
                SELECT id, event_id, event_type, schema_version, aggregate_type,
                       aggregate_id, aggregate_version, store_id, campaign_id,
                       partition_key, occurred_at, payload::text AS payload, attempt_count
                FROM operational_event_outbox
                WHERE id <= :throughOutboxId
                  AND occurred_at >= :trackingStartedAt
                  AND (
                      event_type = 'CAMPAIGN_COMMAND_COMPLETED'
                      OR (event_type = 'COUPON_CLAIM_OUTCOME'
                          AND payload ->> 'result' = 'FAILURE')
                      OR event_type = 'COUPON_REDEMPTION_OUTCOME'
                      OR (event_type = 'PURCHASE_OUTCOME'
                          AND payload ->> 'result' = 'FAILURE')
                      OR (event_type = 'ORDER_STATUS_CHANGED'
                          AND payload ->> 'result' IN ('CANCELED', 'REFUNDED'))
                      OR (event_type = 'INVENTORY_OUTCOME'
                          AND payload ->> 'action' IN ('RESERVATION_FAILED', 'SOLD_OUT'))
                  )
                ORDER BY id
                """, new MapSqlParameterSource()
                .addValue("throughOutboxId", throughOutboxId)
                .addValue("trackingStartedAt", Timestamp.from(trackingStartedAt)),
                (resultSet, rowNumber) -> envelope(resultSet));
    }

    public List<OperationalEventEnvelopeRow> findEventsAfterOutboxId(long outboxId) {
        return jdbcTemplate.query("""
                SELECT id, event_id, event_type, schema_version, aggregate_type,
                       aggregate_id, aggregate_version, store_id, campaign_id,
                       partition_key, occurred_at, payload::text AS payload, attempt_count
                FROM operational_event_outbox
                WHERE id > :outboxId
                ORDER BY id
                """, Map.of("outboxId", outboxId),
                (resultSet, rowNumber) -> envelope(resultSet));
    }

    public List<OperationalEventEnvelopeRow> findLateCommittedEvents(
            long generationId,
            long throughOutboxId
    ) {
        return jdbcTemplate.query("""
                SELECT outbox.id, outbox.event_id, outbox.event_type,
                       outbox.schema_version, outbox.aggregate_type,
                       outbox.aggregate_id, outbox.aggregate_version,
                       outbox.store_id, outbox.campaign_id, outbox.partition_key,
                       outbox.occurred_at, outbox.payload::text AS payload,
                       outbox.attempt_count
                FROM operational_event_outbox outbox
                WHERE outbox.id <= :throughOutboxId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM projection_event_receipts receipt
                      WHERE receipt.generation_id = :generationId
                        AND receipt.projection_name = 'bootstrap-outbox-visibility'
                        AND receipt.event_id = outbox.event_id
                  )
                ORDER BY outbox.id
                """, new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("throughOutboxId", throughOutboxId),
                (resultSet, rowNumber) -> envelope(resultSet));
    }

    public void verifyNoDuplicateReceipts(long generationId) {
        Long duplicateCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT projection_name, event_id
                    FROM projection_event_receipts
                    WHERE generation_id = :generationId
                    GROUP BY projection_name, event_id
                    HAVING COUNT(*) > 1
                ) duplicates
                """, Map.of("generationId", generationId), Long.class);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalStateException(
                    "Duplicate projection receipts for generation: " + generationId);
        }
    }

    public void withExclusiveAdvisoryLock(long lockKey, Runnable action) {
        transaction.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_xact_lock(:lockKey)",
                    Map.of("lockKey", lockKey), Object.class);
            action.run();
        });
    }

    public void activateAtomically(long generationId, Instant activatedAt) {
        Timestamp timestamp = Timestamp.from(activatedAt);
        jdbcTemplate.update("""
                UPDATE projection_generations
                SET status = 'RETIRED', retired_at = :activatedAt
                WHERE status = 'ACTIVE'
                """, Map.of("activatedAt", timestamp));
        int activated = jdbcTemplate.update("""
                UPDATE projection_generations
                SET status = 'ACTIVE', activated_at = :activatedAt
                WHERE id = :generationId AND status = 'BUILDING'
                """, new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("activatedAt", timestamp));
        if (activated != 1) {
            throw new IllegalStateException(
                    "Building projection generation not found: " + generationId);
        }
    }

    public void markBuildingFailed(long generationId) {
        jdbcTemplate.update("""
                UPDATE projection_generations
                SET status = 'FAILED'
                WHERE id = :generationId AND status = 'BUILDING'
                """, Map.of("generationId", generationId));
    }

    public int deleteExpiredRetiredGenerations(Instant cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM projection_generations generation
                WHERE generation.status = 'RETIRED'
                  AND generation.retired_at < :cutoff
                  AND generation.id <> (
                      SELECT newest.id
                      FROM projection_generations newest
                      WHERE newest.status = 'RETIRED'
                      ORDER BY newest.retired_at DESC, newest.id DESC
                      LIMIT 1
                  )
                """, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    public List<OperationalEventEnvelopeRow> lockNextBatch(Instant now, int batchSize) {
        return jdbcTemplate.query("""
                SELECT id, event_id, event_type, schema_version, aggregate_type,
                       aggregate_id, aggregate_version, store_id, campaign_id,
                       partition_key, occurred_at, payload::text AS payload, attempt_count
                FROM operational_event_outbox
                WHERE delivery_state IN ('PENDING', 'RETRY')
                  AND next_attempt_at <= :now
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
                """, new MapSqlParameterSource()
                        .addValue("now", Timestamp.from(now))
                        .addValue("batchSize", batchSize),
                (resultSet, rowNumber) -> envelope(resultSet));
    }

    public boolean hasReceipt(long generationId, String projectionName, UUID eventId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM projection_event_receipts
                    WHERE generation_id = :generationId
                      AND projection_name = :projectionName
                      AND event_id = :eventId
                )
                """, new MapSqlParameterSource()
                        .addValue("generationId", generationId)
                        .addValue("projectionName", projectionName)
                        .addValue("eventId", eventId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public void insertReceipt(long generationId, String projectionName, UUID eventId, Instant processedAt) {
        jdbcTemplate.update("""
                INSERT INTO projection_event_receipts (
                    generation_id, projection_name, event_id, processed_at
                ) VALUES (:generationId, :projectionName, :eventId, :processedAt)
                """, new MapSqlParameterSource()
                        .addValue("generationId", generationId)
                        .addValue("projectionName", projectionName)
                        .addValue("eventId", eventId)
                        .addValue("processedAt", Timestamp.from(processedAt)));
    }

    public void markProcessed(UUID eventId, Instant processedAt) {
        jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = 'PROCESSED', processed_at = :processedAt, last_error = NULL
                WHERE event_id = :eventId
                """, new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("processedAt", Timestamp.from(processedAt)));
    }

    public void markFailed(
            UUID eventId,
            String deliveryState,
            int attemptCount,
            Instant nextAttemptAt,
            String errorSummary
    ) {
        jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = :deliveryState,
                    attempt_count = :attemptCount,
                    next_attempt_at = :nextAttemptAt,
                    last_error = :lastError,
                    processed_at = NULL
                WHERE event_id = :eventId
                """, new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("deliveryState", deliveryState)
                        .addValue("attemptCount", attemptCount)
                        .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
                        .addValue("lastError", errorSummary));
    }

    public int deleteProcessedBefore(Instant cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM operational_event_outbox
                WHERE delivery_state = 'PROCESSED'
                  AND processed_at < :cutoff
                """, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    public ProjectionHealthResponse health(Instant now) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE delivery_state = 'PENDING') AS pending_count,
                       COUNT(*) FILTER (WHERE delivery_state = 'RETRY') AS retry_count,
                       COUNT(*) FILTER (WHERE delivery_state = 'DEAD') AS dead_count,
                       MIN(occurred_at) FILTER (
                           WHERE delivery_state IN ('PENDING', 'RETRY', 'DEAD')
                       ) AS oldest_unprocessed_at,
                       MAX(processed_at) FILTER (
                           WHERE delivery_state = 'PROCESSED'
                       ) AS projection_updated_at
                FROM operational_event_outbox
                """, Map.of(), (resultSet, rowNumber) -> health(resultSet, now));
    }

    private OperationalEventEnvelopeRow envelope(ResultSet resultSet) throws SQLException {
        OperationalEvent event = new OperationalEvent(
                resultSet.getObject("event_id", UUID.class),
                OperationalEventType.valueOf(resultSet.getString("event_type")),
                resultSet.getInt("schema_version"),
                resultSet.getString("aggregate_type"),
                nullableLong(resultSet, "aggregate_id"),
                nullableLong(resultSet, "aggregate_version"),
                nullableLong(resultSet, "store_id"),
                nullableLong(resultSet, "campaign_id"),
                resultSet.getString("partition_key"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                payload(resultSet.getString("payload"))
        );
        return new OperationalEventEnvelopeRow(
                resultSet.getLong("id"), event, resultSet.getInt("attempt_count"));
    }

    private JsonNode payload(String serializedPayload) throws SQLException {
        try {
            return objectMapper.readTree(serializedPayload);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to deserialize operational event payload", exception);
        }
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static ProjectionHealthResponse health(ResultSet resultSet, Instant now) throws SQLException {
        Timestamp oldest = resultSet.getTimestamp("oldest_unprocessed_at");
        Timestamp updated = resultSet.getTimestamp("projection_updated_at");
        Instant oldestInstant = oldest == null ? null : oldest.toInstant();
        long lagSeconds = oldestInstant == null
                ? 0
                : Math.max(0, Duration.between(oldestInstant, now).getSeconds());
        return new ProjectionHealthResponse(
                resultSet.getLong("pending_count"),
                resultSet.getLong("retry_count"),
                resultSet.getLong("dead_count"),
                oldestInstant,
                lagSeconds,
                updated == null ? null : updated.toInstant()
        );
    }
}
