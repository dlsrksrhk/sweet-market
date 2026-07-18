package com.sweet.market.gateway.migration;

import com.sweet.market.gateway.support.GatewayIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
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
