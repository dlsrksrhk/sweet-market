package com.sweet.market.store.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OperationsDashboardMigrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        assertThat(indexMetadata("idx_projection_event_receipts_generation_processed_at"))
                .isEqualTo(index(
                        column("generation_id", "ASC", "LAST"),
                        column("processed_at", "DESC", "FIRST")));
        assertThat(indexMetadata("idx_campaign_metric_hourly_store_bucket"))
                .isEqualTo(index(
                        column("generation_id", "ASC", "LAST"),
                        column("commerce_store_id", "ASC", "LAST"),
                        column("bucket_start", "ASC", "LAST"),
                        column("campaign_kind", "ASC", "LAST"),
                        column("campaign_id", "ASC", "LAST")));
        assertThat(indexMetadata("idx_campaign_metric_hourly_owner_store_bucket"))
                .isEqualTo(index(
                        column("generation_id", "ASC", "LAST"),
                        column("campaign_owner_store_id", "ASC", "LAST"),
                        column("bucket_start", "ASC", "LAST"),
                        column("campaign_kind", "ASC", "LAST"),
                        column("campaign_id", "ASC", "LAST")));
        assertThat(indexExists("idx_inventory_pressure_store_attention")).isTrue();
        assertThat(indexMetadata("idx_campaign_audit_projection_owner_time"))
                .isEqualTo(index(
                        column("generation_id", "ASC", "LAST"),
                        column("owner_store_id", "ASC", "LAST"),
                        column("occurred_at", "DESC", "FIRST"),
                        column("aggregate_version", "DESC", "LAST"),
                        column("event_id", "DESC", "FIRST")));
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

        assertIndexAccess(explain("""
                SELECT generation.id, generation.tracking_started_at,
                       COALESCE(receipt.processed_at, generation.activated_at,
                                generation.cutoff_at) AS projection_updated_at
                FROM projection_generations generation
                LEFT JOIN LATERAL (
                    SELECT projection_receipt.processed_at
                    FROM projection_event_receipts projection_receipt
                    WHERE projection_receipt.generation_id = generation.id
                    ORDER BY projection_receipt.processed_at DESC
                    LIMIT 1
                ) receipt ON TRUE
                WHERE generation.status = 'ACTIVE'
                """), "idx_projection_event_receipts_generation_processed_at");
        Map<String, String> campaignPlan = explain("""
                SELECT campaign_id
                FROM campaign_metric_hourly
                WHERE generation_id = 7
                  AND bucket_start >= '2026-07-01T00:00:00Z'
                  AND bucket_start < '2026-08-01T00:00:00Z'
                  AND (campaign_owner_store_id = 11 OR commerce_store_id = 11)
                """);
        assertIndexAccess(campaignPlan, "idx_campaign_metric_hourly_owner_store_bucket");
        assertIndexAccess(campaignPlan, "idx_campaign_metric_hourly_store_bucket");
        assertIndexAccess(explain("""
                SELECT event_id
                FROM campaign_audit_projection
                WHERE generation_id = 7 AND owner_store_id = 11
                  AND occurred_at >= '2026-07-10T00:00:00Z'
                  AND occurred_at < '2026-07-20T00:00:00Z'
                ORDER BY occurred_at DESC, aggregate_version DESC NULLS LAST, event_id DESC
                LIMIT 20
                """), "idx_campaign_audit_projection_owner_time");
    }

    private boolean tableExists(String tableName) throws SQLException {
        return queryBoolean("SELECT to_regclass('public." + tableName + "') IS NOT NULL");
    }

    private boolean indexExists(String indexName) throws SQLException {
        return queryBoolean("SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = '"
                + indexName + "')");
    }

    private IndexMetadata indexMetadata(String indexName) throws SQLException {
        String sql = """
                SELECT access_method.amname,
                       keys.ordinality,
                       attribute.attname,
                       (COALESCE(keys.index_option, 0) & 1) = 1 AS descending,
                       (COALESCE(keys.index_option, 0) & 2) = 2 AS nulls_first,
                       keys.ordinality <= index_metadata.indnkeyatts AS key_column,
                       pg_get_expr(index_metadata.indpred, index_metadata.indrelid) AS predicate
                FROM pg_class index_class
                JOIN pg_namespace namespace ON namespace.oid = index_class.relnamespace
                JOIN pg_index index_metadata ON index_metadata.indexrelid = index_class.oid
                JOIN pg_am access_method ON access_method.oid = index_class.relam
                CROSS JOIN LATERAL unnest(
                    index_metadata.indkey::smallint[],
                    index_metadata.indoption::smallint[]
                ) WITH ORDINALITY AS keys(attribute_number, index_option, ordinality)
                LEFT JOIN pg_attribute attribute
                  ON attribute.attrelid = index_metadata.indrelid
                 AND attribute.attnum = keys.attribute_number
                WHERE namespace.nspname = 'public'
                  AND index_class.relname = ?
                ORDER BY keys.ordinality
                """;
        try (Connection connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, indexName);
            try (var resultSet = statement.executeQuery()) {
                List<IndexColumn> keyColumns = new java.util.ArrayList<>();
                List<String> includeColumns = new java.util.ArrayList<>();
                String accessMethod = null;
                String predicate = null;
                while (resultSet.next()) {
                    accessMethod = resultSet.getString("amname");
                    predicate = resultSet.getString("predicate");
                    String columnName = resultSet.getString("attname");
                    if (resultSet.getBoolean("key_column")) {
                        keyColumns.add(column(
                                columnName,
                                resultSet.getBoolean("descending") ? "DESC" : "ASC",
                                resultSet.getBoolean("nulls_first") ? "FIRST" : "LAST"));
                    } else {
                        includeColumns.add(columnName);
                    }
                }
                assertThat(accessMethod).as(indexName + " exists").isNotNull();
                return new IndexMetadata(accessMethod, keyColumns, includeColumns, predicate);
            }
        }
    }

    private IndexMetadata index(IndexColumn... columns) {
        return new IndexMetadata("btree", List.of(columns), List.of(), null);
    }

    private IndexColumn column(String name, String direction, String nulls) {
        return new IndexColumn(name, direction, nulls);
    }

    private Map<String, String> explain(String query) throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            try (var resultSet = statement.executeQuery("EXPLAIN (FORMAT JSON) " + query)) {
                assertThat(resultSet.next()).isTrue();
                JsonNode root;
                try {
                    root = OBJECT_MAPPER.readTree(resultSet.getString(1)).get(0).path("Plan");
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new SQLException("PostgreSQL returned invalid JSON EXPLAIN output", exception);
                }
                Map<String, String> accesses = new LinkedHashMap<>();
                collectIndexAccesses(root, accesses);
                return accesses;
            }
        }
    }

    private void collectIndexAccesses(JsonNode plan, Map<String, String> accesses) {
        if (plan.hasNonNull("Index Name")) {
            accesses.put(plan.path("Index Name").asText(), plan.path("Node Type").asText());
        }
        for (JsonNode child : plan.path("Plans")) {
            collectIndexAccesses(child, accesses);
        }
    }

    private void assertIndexAccess(Map<String, String> accesses, String indexName) {
        assertThat(accesses).containsKey(indexName);
        assertThat(accesses.get(indexName)).isIn("Index Scan", "Index Only Scan", "Bitmap Index Scan");
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

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
    }

    private record IndexMetadata(
            String accessMethod,
            List<IndexColumn> keyColumns,
            List<String> includeColumns,
            String predicate
    ) {
    }

    private record IndexColumn(String name, String direction, String nulls) {
    }
}
