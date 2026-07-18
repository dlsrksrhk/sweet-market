package com.sweet.market.gateway.security;

import com.sweet.market.gateway.support.GatewayIntegrationTestSupport;
import com.sweet.market.gateway.support.GatewaySecurityTestConfiguration;
import com.sweet.market.gateway.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.sweet.market.gateway.support.GatewaySecurityTestConfiguration.VECTOR_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "gateway.integration-security.allowed-clock-skew=PT5M",
        "gateway.integration-security.max-body-bytes=1048576",
        "gateway.integration-security.replay-retention=PT10M",
        "gateway.integration-security.cleanup-batch-size=1000",
        "gateway.integration-security.clients[0].client-id=sweet-market",
        "gateway.integration-security.clients[0].api-key=m32-test-api-key",
        "gateway.integration-security.clients[0].keys[0].key-id=m32-test-key-1",
        "gateway.integration-security.clients[0].keys[0].secret=m32-test-hmac-secret-32bytes-minimum",
        "gateway.integration-security.clients[0].keys[1].key-id=m32-test-key-2",
        "gateway.integration-security.clients[0].keys[1].secret=m32-next-hmac-secret-32bytes-minimum"
})
@Import(GatewaySecurityTestConfiguration.class)
class ReplayCleanupSchedulerTest extends GatewayIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReplayCleanupScheduler scheduler;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM integration_request_replays");
        clock.setInstant(VECTOR_INSTANT);
    }

    @Test
    void 만료된_replay를_한번에_1000개까지만_정리한다() {
        List<UUID> expiredRequestIds = new ArrayList<>();
        for (int index = 0; index < 1_002; index++) {
            UUID requestId = UUID.randomUUID();
            expiredRequestIds.add(requestId);
            insertReplay(requestId, VECTOR_INSTANT.minusSeconds(2_000L - index));
        }
        insertReplay(UUID.randomUUID(), VECTOR_INSTANT.plusSeconds(60));

        int deleted = scheduler.cleanupExpiredReplays();

        assertThat(deleted).isEqualTo(1_000);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_request_replays WHERE expires_at <= ?",
                Integer.class,
                Timestamp.from(VECTOR_INSTANT)
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT request_id FROM integration_request_replays WHERE expires_at <= ? ORDER BY expires_at, id",
                UUID.class,
                Timestamp.from(VECTOR_INSTANT)
        )).containsExactlyElementsOf(expiredRequestIds.subList(1_000, 1_002));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_request_replays",
                Integer.class
        )).isEqualTo(3);
    }

    private void insertReplay(UUID requestId, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO integration_request_replays(client_id, request_id, received_at, expires_at) VALUES (?, ?, ?, ?)",
                "sweet-market",
                requestId,
                Timestamp.from(VECTOR_INSTANT.minusSeconds(3_000)),
                Timestamp.from(expiresAt)
        );
    }
}
