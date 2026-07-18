package com.sweet.market.operations.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

@Repository
public class JdbcOperationalEventRecorder implements OperationalEventRecorder {

    private static final long ADVISORY_LOCK_KEY = 310031L;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOperationalEventRecorder(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(OperationalEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.eventType() == OperationalEventType.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN operational event type must not be recorded");
        }
        acquireSharedTransactionLock();
        jdbcTemplate.update("""
                INSERT INTO operational_event_outbox (
                    event_id, event_type, schema_version, aggregate_type, aggregate_id,
                    aggregate_version, store_id, campaign_id, partition_key, occurred_at, payload
                ) VALUES (
                    :eventId, :eventType, :schemaVersion, :aggregateType, :aggregateId,
                    :aggregateVersion, :storeId, :campaignId, :partitionKey, :occurredAt,
                    CAST(:payload AS JSONB)
                )
                """, parameters(event));
    }

    private void acquireSharedTransactionLock() {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock_shared(:lockKey)",
                Map.of("lockKey", ADVISORY_LOCK_KEY),
                resultSet -> {
                    resultSet.next();
                    return null;
                }
        );
    }

    private MapSqlParameterSource parameters(OperationalEvent event) {
        try {
            return new MapSqlParameterSource()
                    .addValue("eventId", event.eventId())
                    .addValue("eventType", event.eventType().name())
                    .addValue("schemaVersion", event.schemaVersion())
                    .addValue("aggregateType", event.aggregateType())
                    .addValue("aggregateId", event.aggregateId())
                    .addValue("aggregateVersion", event.aggregateVersion())
                    .addValue("storeId", event.storeId())
                    .addValue("campaignId", event.campaignId())
                    .addValue("partitionKey", event.partitionKey())
                    .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                    .addValue("payload", objectMapper.writeValueAsString(event.payload()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize operational event payload", exception);
        }
    }
}
