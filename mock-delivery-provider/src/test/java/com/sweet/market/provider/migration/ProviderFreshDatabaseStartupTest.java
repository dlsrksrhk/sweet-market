package com.sweet.market.provider.migration;

import com.sweet.market.provider.support.ProviderIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProviderFreshDatabaseStartupTest extends ProviderIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 제공자_재생_요청_테이블과_고유_제약_조건이_생성된다() {
        Boolean tableExists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = 'integration_request_replays'
                )
                """, Boolean.class);
        Boolean constraintExists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_constraint
                    WHERE conname = 'uq_provider_replay_client_request'
                )
                """, Boolean.class);

        assertThat(tableExists).isTrue();
        assertThat(constraintExists).isTrue();
    }
}
