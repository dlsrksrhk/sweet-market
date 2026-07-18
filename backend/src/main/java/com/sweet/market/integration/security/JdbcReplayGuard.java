package com.sweet.market.integration.security;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class JdbcReplayGuard implements ReplayGuard {

    private final JdbcTemplate jdbcTemplate;

    public JdbcReplayGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryClaim(
            ExternalSystem source,
            UUID requestId,
            Instant receivedAt,
            Instant expiresAt
    ) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO external_integration_request_replays (
                        source, request_id, received_at, expires_at
                    ) VALUES (?, ?, ?, ?)
                    """, source.name(), requestId, Timestamp.from(receivedAt), Timestamp.from(expiresAt)) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public int deleteExpired(Instant cutoff, int limit) {
        return jdbcTemplate.update("""
                WITH expired AS (
                    SELECT id
                    FROM external_integration_request_replays
                    WHERE expires_at <= ?
                    ORDER BY expires_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM external_integration_request_replays replay
                USING expired
                WHERE replay.id = expired.id
                """, Timestamp.from(cutoff), limit);
    }
}
