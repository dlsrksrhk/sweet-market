package com.sweet.market.gateway.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcReplayGuard implements ReplayGuard {

    private final JdbcTemplate jdbcTemplate;

    public JdbcReplayGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryClaim(String clientId, UUID requestId, Instant receivedAt, Instant expiresAt) {
        return jdbcTemplate.update(
                """
                        INSERT INTO integration_request_replays(client_id, request_id, received_at, expires_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (client_id, request_id) DO NOTHING
                        """,
                clientId,
                requestId,
                Timestamp.from(receivedAt),
                Timestamp.from(expiresAt)
        ) == 1;
    }

    @Override
    public int deleteExpired(Instant now, int limit) {
        return jdbcTemplate.update(
                """
                        DELETE FROM integration_request_replays
                        WHERE id IN (
                            SELECT id
                            FROM integration_request_replays
                            WHERE expires_at <= ?
                            ORDER BY expires_at, id
                            LIMIT ?
                        )
                        """,
                Timestamp.from(now),
                limit
        );
    }
}
