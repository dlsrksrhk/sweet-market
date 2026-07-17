package com.sweet.market.store.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OperationsDashboardMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("operations_dashboard_migration_test")
            .withUsername("market")
            .withPassword("market");

    @Test
    void 운영_대시보드_이벤트와_집계_스키마를_생성한다() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(tableExists("operational_event_outbox")).isTrue();
        assertThat(tableExists("projection_generations")).isTrue();
        assertThat(tableExists("projection_event_receipts")).isTrue();
        assertThat(tableExists("store_metric_hourly")).isTrue();
        assertThat(tableExists("campaign_metric_hourly")).isTrue();
        assertThat(tableExists("inventory_pressure_projection")).isTrue();
        assertThat(tableExists("inventory_failure_hourly")).isTrue();
        assertThat(tableExists("campaign_audit_projection")).isTrue();
        assertThat(tableExists("performance_measurement_runs")).isTrue();
        assertThat(tableExists("performance_endpoint_metrics")).isTrue();
        assertThat(tableExists("performance_query_evidence")).isTrue();
        assertThat(indexExists("idx_operational_event_outbox_poll")).isTrue();
        assertThat(indexExists("idx_campaign_metric_hourly_store_bucket")).isTrue();
        assertThat(indexExists("idx_inventory_pressure_store_attention")).isTrue();
        assertThat(uniqueConstraintExists("uq_projection_event_receipts_generation_projection_event")).isTrue();
    }

    private boolean tableExists(String tableName) throws SQLException {
        return queryBoolean("SELECT to_regclass('public." + tableName + "') IS NOT NULL");
    }

    private boolean indexExists(String indexName) throws SQLException {
        return queryBoolean("SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = '"
                + indexName + "')");
    }

    private boolean uniqueConstraintExists(String constraintName) throws SQLException {
        return queryBoolean("SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'u' AND conname = '"
                + constraintName + "')");
    }

    private boolean queryBoolean(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        ); var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
