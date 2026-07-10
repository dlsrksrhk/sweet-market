package com.sweet.market.store.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.task.scheduling.enabled=false",
                "jwt.secret=sweet-market-test-secret-key-32bytes-minimum"
        }
)
class StoreFreshDatabaseStartupTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("store_fresh_database_test")
            .withUsername("market")
            .withPassword("market");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Test
    void 빈_PostgreSQL에서도_Flyway와_JPA_업데이트로_애플리케이션이_시작된다() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(tableExists("stores")).isTrue();
        assertThat(tableExists("members")).isTrue();
        assertThat(tableExists("products")).isTrue();
        assertThat(tableExists("orders")).isTrue();
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
}
