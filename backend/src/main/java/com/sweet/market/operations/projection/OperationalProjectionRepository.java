package com.sweet.market.operations.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class OperationalProjectionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OperationalProjectionRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
