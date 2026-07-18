package com.sweet.market.provider.security;

import com.sweet.market.provider.support.ProviderIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "provider.integration-security.allowed-clock-skew=PT5M",
        "provider.integration-security.max-body-bytes=1048576",
        "provider.integration-security.replay-retention=PT10M",
        "provider.integration-security.cleanup-batch-size=1000",
        "provider.integration-security.clients[0].client-id=sweet-market",
        "provider.integration-security.clients[0].api-key=provider-api-key",
        "provider.integration-security.clients[0].keys[0].key-id=current-key",
        "provider.integration-security.clients[0].keys[0].secret=current-secret-32-bytes-minimum-value"
})
@Import(ReplayCleanupSchedulerTest.FixedClockConfiguration.class)
class ReplayCleanupSchedulerTest extends ProviderIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReplayCleanupScheduler scheduler;

    @BeforeEach
    void clearReplays() {
        jdbcTemplate.update("DELETE FROM integration_request_replays");
    }

    @Test
    void 만료된_replay를_한번에_1000개까지만_정리한다() {
        jdbcTemplate.update("""
                INSERT INTO integration_request_replays(client_id, request_id, received_at, expires_at)
                SELECT 'sweet-market', gen_random_uuid(), ?::timestamptz, ?::timestamptz
                FROM generate_series(1, 1001)
                """, Timestamp.from(NOW.minusSeconds(1200)), Timestamp.from(NOW.minusSeconds(1)));
        jdbcTemplate.update("""
                INSERT INTO integration_request_replays(client_id, request_id, received_at, expires_at)
                VALUES ('sweet-market', gen_random_uuid(), ?::timestamptz, ?::timestamptz)
                """, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(600)));

        int deleted = scheduler.cleanupExpiredReplays();

        assertThat(deleted).isEqualTo(1000);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_request_replays WHERE expires_at <= ?",
                Integer.class, Timestamp.from(NOW))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_request_replays WHERE expires_at > ?",
                Integer.class, Timestamp.from(NOW))).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock providerCleanupTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
