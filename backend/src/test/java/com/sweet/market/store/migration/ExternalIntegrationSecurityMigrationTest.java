package com.sweet.market.store.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ExternalIntegrationSecurityMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("external_integration_migration_test")
            .withUsername("market")
            .withPassword("market");

    @BeforeAll
    static void migration을_적용한다() {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void source_request_유일성과_만료_index를_생성한다() throws SQLException {
        try (Connection connection = connection()) {
            connection.createStatement().execute("""
                    INSERT INTO external_integration_request_replays (source, request_id, received_at, expires_at)
                    VALUES ('PAYMENT_GATEWAY', '3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41', now(), now() + interval '10 minutes'),
                           ('DELIVERY_PROVIDER', '3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41', now(), now() + interval '10 minutes')
                    """);

            assertThatThrownBy(() -> connection.createStatement().execute("""
                    INSERT INTO external_integration_request_replays (source, request_id, received_at, expires_at)
                    VALUES ('PAYMENT_GATEWAY', '3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41', now(), now() + interval '10 minutes')
                    """))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_external_replay_source_request");
        }

        try (Connection connection = connection()) {
            connection.createStatement().execute("""
                    INSERT INTO external_integration_request_replays (source, request_id, received_at, expires_at)
                    SELECT 'PAYMENT_GATEWAY', md5('replay-' || value)::uuid, now(),
                           now() - interval '1 minute' + value * interval '1 millisecond'
                    FROM generate_series(1, 5000) value
                    ON CONFLICT DO NOTHING
                    """);
            connection.createStatement().execute("ANALYZE external_integration_request_replays");
            connection.createStatement().execute("SET enable_seqscan = off");
            try (var resultSet = connection.createStatement().executeQuery("""
                    EXPLAIN SELECT id
                    FROM external_integration_request_replays
                    WHERE expires_at <= now()
                    ORDER BY expires_at, id
                    LIMIT 1000
                    """)) {
                StringBuilder plan = new StringBuilder();
                while (resultSet.next()) {
                    plan.append(resultSet.getString(1)).append('\n');
                }
                assertThat(plan.toString()).contains("idx_external_replay_expiry");
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }
}
