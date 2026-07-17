package com.sweet.market.store.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
        assertThat(columnExists("campaign_metric_hourly", "purchase_failure_count")).isTrue();
        assertThat(tableExists("inventory_pressure_projection")).isTrue();
        assertThat(tableExists("inventory_failure_hourly")).isTrue();
        assertThat(tableExists("campaign_audit_projection")).isTrue();
        assertThat(tableExists("performance_measurement_runs")).isTrue();
        assertThat(tableExists("performance_endpoint_metrics")).isTrue();
        assertThat(tableExists("performance_query_evidence")).isTrue();
        assertThat(indexExists("idx_operational_event_outbox_poll")).isTrue();
        assertThat(indexDefinition("idx_projection_event_receipts_generation_processed_at"))
                .contains("generation_id", "processed_at DESC");
        assertThat(indexExists("idx_campaign_metric_hourly_store_bucket")).isTrue();
        assertThat(indexDefinition("idx_campaign_metric_hourly_owner_store_bucket"))
                .contains("generation_id", "campaign_owner_store_id", "bucket_start");
        assertThat(indexExists("idx_inventory_pressure_store_attention")).isTrue();
        assertThat(indexDefinition("idx_campaign_audit_projection_owner_time"))
                .contains("occurred_at DESC", "aggregate_version DESC NULLS LAST", "event_id DESC");
        assertThat(uniqueConstraintExists("uq_projection_event_receipts_generation_projection_event")).isTrue();
    }

    @Test
    void 운영_대시보드_대표조회는_범위인덱스를_사용한다() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        seedRepresentativeRows();

        assertThat(explain("""
                SELECT MAX(processed_at)
                FROM projection_event_receipts
                WHERE generation_id = 7
                """))
                .contains("idx_projection_event_receipts_generation_processed_at");
        assertThat(explain("""
                SELECT campaign_id
                FROM campaign_metric_hourly
                WHERE generation_id = 7
                  AND bucket_start >= '2026-07-01T00:00:00Z'
                  AND bucket_start < '2026-08-01T00:00:00Z'
                  AND (campaign_owner_store_id = 11 OR commerce_store_id = 11)
                """))
                .contains("idx_campaign_metric_hourly_owner_store_bucket")
                .contains("idx_campaign_metric_hourly_store_bucket");
        assertThat(explain("""
                SELECT event_id
                FROM campaign_audit_projection
                WHERE generation_id = 7 AND owner_store_id = 11
                ORDER BY occurred_at DESC, aggregate_version DESC NULLS LAST, event_id DESC
                LIMIT 20
                """))
                .contains("idx_campaign_audit_projection_owner_time");
    }

    private boolean tableExists(String tableName) throws SQLException {
        return queryBoolean("SELECT to_regclass('public." + tableName + "') IS NOT NULL");
    }

    private boolean indexExists(String indexName) throws SQLException {
        return queryBoolean("SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = '"
                + indexName + "')");
    }

    private String indexDefinition(String indexName) throws SQLException {
        return queryString("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = '"
                + indexName + "'");
    }

    private String explain(String query) throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            List<String> lines = new ArrayList<>();
            try (var resultSet = statement.executeQuery("EXPLAIN " + query)) {
                while (resultSet.next()) {
                    lines.add(resultSet.getString(1));
                }
            }
            return String.join("\n", lines);
        }
    }

    private void seedRepresentativeRows() throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO projection_generations (
                        id, status, cutoff_at, tracking_started_at, bootstrap_high_water_id, activated_at
                    ) VALUES (7, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP)
                    ON CONFLICT (id) DO NOTHING
                    """);
            statement.execute("""
                    INSERT INTO projection_event_receipts (
                        generation_id, projection_name, event_id, processed_at
                    )
                    SELECT 7, 'migration-plan', md5('receipt-' || value)::uuid,
                           '2026-07-01T00:00:00Z'::timestamptz + value * interval '1 minute'
                    FROM generate_series(1, 1000) value
                    ON CONFLICT DO NOTHING
                    """);
            statement.execute("""
                    INSERT INTO campaign_metric_hourly (
                        generation_id, bucket_start, commerce_store_id, campaign_kind,
                        campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
                    )
                    SELECT 7,
                           '2026-07-01T00:00:00Z'::timestamptz + (value % 720) * interval '1 hour',
                           CASE WHEN value % 103 = 0 THEN 11 ELSE 98 END,
                           'COUPON', value, 'STORE',
                           CASE WHEN value % 101 = 0 THEN 11 ELSE 99 END,
                           'NONE'
                    FROM generate_series(1, 5000) value
                    ON CONFLICT DO NOTHING
                    """);
            statement.execute("""
                    INSERT INTO campaign_audit_projection (
                        generation_id, event_id, campaign_kind, campaign_id, owner_type,
                        owner_store_id, actor_member_id, command, occurred_at,
                        aggregate_version, after_summary
                    )
                    SELECT 7, md5('audit-' || value)::uuid, 'COUPON', value, 'STORE',
                           CASE WHEN value % 101 = 0 THEN 11 ELSE 99 END,
                           1, 'CREATE',
                           '2026-07-01T00:00:00Z'::timestamptz + (value % 720) * interval '1 hour',
                           value, '{}'::jsonb
                    FROM generate_series(1, 5000) value
                    ON CONFLICT DO NOTHING
                    """);
            statement.execute("ANALYZE projection_event_receipts");
            statement.execute("ANALYZE campaign_metric_hourly");
            statement.execute("ANALYZE campaign_audit_projection");
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        return queryBoolean("SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND table_name = '" + tableName + "' AND column_name = '" + columnName + "')");
    }

    private boolean uniqueConstraintExists(String constraintName) throws SQLException {
        return queryBoolean("SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'u' AND conname = '"
                + constraintName + "')");
    }

    private boolean queryBoolean(String sql) throws SQLException {
        try (Connection connection = connection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = connection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
    }
}
