package com.sweet.market.operations.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminOperationalEventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminOperationalEventService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<DeadOperationalEventResponse> findDead(int page, int size) {
        PageRequest pageRequest = pageRequest(page, size);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());
        List<DeadOperationalEventResponse> content = jdbcTemplate.query("""
                SELECT id, event_id, event_type, schema_version, aggregate_type,
                       aggregate_id, aggregate_version, store_id, campaign_id,
                       partition_key, occurred_at, payload::text AS payload,
                       delivery_state, attempt_count, next_attempt_at, last_error, created_at
                FROM operational_event_outbox
                WHERE delivery_state = 'DEAD'
                ORDER BY occurred_at ASC, id ASC
                LIMIT :limit OFFSET :offset
                """, parameters, (resultSet, rowNumber) -> deadEvent(resultSet));
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM operational_event_outbox WHERE delivery_state = 'DEAD'
                """, Map.of(), Long.class);
        return new PageImpl<>(content, pageRequest, total == null ? 0 : total);
    }

    @Transactional
    public void retry(UUID eventId, Long actorMemberId, Instant now) {
        if (eventId == null || actorMemberId == null || now == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int updated = jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = 'RETRY',
                    attempt_count = 0,
                    next_attempt_at = :now,
                    last_error = NULL
                WHERE event_id = :eventId
                  AND delivery_state = 'DEAD'
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("now", Timestamp.from(now)));
        if (updated == 1) {
            return;
        }
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM operational_event_outbox WHERE event_id = :eventId
                )
                """, Map.of("eventId", eventId), Boolean.class);
        throw new BusinessException(Boolean.TRUE.equals(exists)
                ? ErrorCode.OPERATIONAL_EVENT_RETRY_NOT_ALLOWED
                : ErrorCode.OPERATIONAL_EVENT_NOT_FOUND);
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
    }

    private DeadOperationalEventResponse deadEvent(ResultSet resultSet) throws SQLException {
        return new DeadOperationalEventResponse(
                resultSet.getLong("id"),
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getInt("schema_version"),
                resultSet.getString("aggregate_type"),
                nullableLong(resultSet, "aggregate_id"),
                nullableLong(resultSet, "aggregate_version"),
                nullableLong(resultSet, "store_id"),
                nullableLong(resultSet, "campaign_id"),
                resultSet.getString("partition_key"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                payload(resultSet.getString("payload")),
                resultSet.getString("delivery_state"),
                resultSet.getInt("attempt_count"),
                resultSet.getTimestamp("next_attempt_at").toInstant(),
                resultSet.getString("last_error"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private JsonNode payload(String serializedPayload) throws SQLException {
        try {
            return objectMapper.readTree(serializedPayload);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to deserialize dead operational event payload", exception);
        }
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
