package com.sweet.market.integration.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ReplayCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("external_replay_cleanup_test")
            .withUsername("market")
            .withPassword("market");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void 테이블을_준비한다() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE external_integration_request_replays (
                    id BIGSERIAL PRIMARY KEY,
                    source VARCHAR(40) NOT NULL,
                    request_id UUID NOT NULL,
                    received_at TIMESTAMPTZ NOT NULL,
                    expires_at TIMESTAMPTZ NOT NULL,
                    CONSTRAINT uq_external_replay_source_request UNIQUE (source, request_id)
                )
                """);
    }

    @Test
    void 만료된_source_replay를_한번에_1000개까지만_정리한다() {
        jdbcTemplate.update("DELETE FROM external_integration_request_replays");
        jdbcTemplate.update("""
                INSERT INTO external_integration_request_replays (source, request_id, received_at, expires_at)
                SELECT 'PAYMENT_GATEWAY', md5('expired-' || value)::uuid, ?::timestamptz, ?::timestamptz
                FROM generate_series(1, 1001) value
                """, Timestamp.from(NOW.minusSeconds(1_200)), Timestamp.from(NOW.minusSeconds(1)));
        jdbcTemplate.update("""
                INSERT INTO external_integration_request_replays (source, request_id, received_at, expires_at)
                VALUES ('DELIVERY_PROVIDER', gen_random_uuid(), ?::timestamptz, ?::timestamptz)
                """, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(600)));
        ReplayCleanupScheduler scheduler = new ReplayCleanupScheduler(
                new JdbcReplayGuard(jdbcTemplate), Clock.fixed(NOW, ZoneOffset.UTC), 1_000);

        int deleted = scheduler.cleanupExpired();

        assertThat(deleted).isEqualTo(1_000);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM external_integration_request_replays", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM external_integration_request_replays WHERE expires_at <= ?",
                Integer.class, Timestamp.from(NOW))).isEqualTo(1);
    }
}
