package com.sweet.market.store.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CouponRedemptionMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("coupon_redemption_migration_test")
            .withUsername("market")
            .withPassword("market");

    @Test
    void 쿠폰_예약과_주문_쿠폰_스냅샷_스키마를_생성한다() throws SQLException {
        createBaselineTables();

        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connection()) {
            assertThat(tableExists(connection, "coupon_reservations")).isTrue();
            assertThat(columnExists(connection, "coupon_reservations", "member_coupon_id")).isTrue();
            assertThat(columnExists(connection, "coupon_reservations", "order_id")).isTrue();
            assertThat(columnExists(connection, "coupon_reservations", "status")).isTrue();
            assertThat(columnExists(connection, "coupon_reservations", "reserved_at")).isTrue();
            assertThat(columnExists(connection, "coupon_reservations", "expires_at")).isTrue();
            assertThat(columnExists(connection, "coupon_reservations", "consumed_at")).isTrue();
            assertThat(columnExists(connection, "coupon_reservations", "released_at")).isTrue();
            assertThat(columnExists(connection, "orders", "member_coupon_id")).isTrue();
            assertThat(columnExists(connection, "orders", "coupon_discount_amount")).isTrue();
            assertThat(constraintExists(connection, "chk_coupon_reservations_status")).isTrue();
            assertThat(indexExists(connection, "coupon_reservations", "uq_coupon_reservations_active_member_coupon")).isTrue();
            assertThat(indexExists(connection, "coupon_reservations", "idx_coupon_reservations_reserved_expires_at")).isTrue();
        }
    }

    private void createBaselineTables() throws SQLException {
        try (Connection connection = connection()) {
            connection.createStatement().execute("CREATE TABLE members (id BIGINT PRIMARY KEY, nickname VARCHAR(30) NOT NULL, role VARCHAR(20) NOT NULL)");
            connection.createStatement().execute("CREATE TABLE products (id BIGINT PRIMARY KEY, seller_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE', price BIGINT NOT NULL)");
            connection.createStatement().execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL)");
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        return queryBoolean(connection, "SELECT to_regclass('public." + tableName + "') IS NOT NULL");
    }

    private boolean constraintExists(Connection connection, String constraintName) throws SQLException {
        return queryBoolean(connection, "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '" + constraintName + "')");
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        return queryBoolean(connection, """
                SELECT EXISTS (
                    SELECT 1 FROM pg_indexes
                    WHERE schemaname = 'public' AND tablename = '%s' AND indexname = '%s'
                )
                """.formatted(tableName, indexName));
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        return queryBoolean(connection, """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = '%s' AND column_name = '%s'
                )
                """.formatted(tableName, columnName));
    }

    private boolean queryBoolean(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
