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
class CouponMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("coupon_migration_test")
            .withUsername("market")
            .withPassword("market");

    @Test
    void 쿠폰_캠페인과_대상과_회원쿠폰_스키마를_생성한다() throws SQLException {
        createBaselineTables();

        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connection()) {
            assertThat(tableExists(connection, "coupon_campaigns")).isTrue();
            assertThat(tableExists(connection, "coupon_campaign_targets")).isTrue();
            assertThat(tableExists(connection, "member_coupons")).isTrue();
            assertThat(constraintExists(connection, "uq_coupon_campaign_targets_campaign_product")).isTrue();
            assertThat(constraintExists(connection, "uq_member_coupons_campaign_member")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "issued_at")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "valid_until")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "discount_type")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "discount_value")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "max_discount_amount")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "minimum_purchase_amount")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "scope")).isTrue();
            assertThat(columnExists(connection, "member_coupons", "stackable")).isTrue();
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
