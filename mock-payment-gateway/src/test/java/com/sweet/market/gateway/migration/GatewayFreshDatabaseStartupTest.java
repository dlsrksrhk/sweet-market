package com.sweet.market.gateway.migration;

import com.sweet.market.gateway.support.GatewayIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "gateway.integration-security.allowed-clock-skew=PT5M",
                "gateway.integration-security.max-body-bytes=1048576",
                "gateway.integration-security.replay-retention=PT10M",
                "gateway.integration-security.cleanup-batch-size=1000",
                "gateway.integration-security.clients[0].client-id=sweet-market",
                "gateway.integration-security.clients[0].api-key=migration-test-api-key",
                "gateway.integration-security.clients[0].keys[0].key-id=migration-current-key",
                "gateway.integration-security.clients[0].keys[0].secret=migration-current-secret-32bytes-minimum",
                "gateway.integration-security.clients[0].keys[1].key-id=migration-next-key",
                "gateway.integration-security.clients[0].keys[1].secret=migration-next-secret-32bytes-minimum"
        }
)
class GatewayFreshDatabaseStartupTest extends GatewayIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 재생_요청_테이블과_고유_제약_조건을_생성한다() {
        assertThat(tableExists("integration_request_replays")).isTrue();
        assertThat(uniqueConstraintExists("uq_gateway_replay_client_request")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                Integer.class,
                tableName
        );
        return count != null && count == 1;
    }

    private boolean uniqueConstraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pg_constraint
                        WHERE conname = ?
                          AND contype = 'u'
                        """,
                Integer.class,
                constraintName
        );
        return count != null && count == 1;
    }
}
