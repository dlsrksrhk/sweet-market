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
class PromotionMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("promotion_migration_test")
            .withUsername("market")
            .withPassword("market");

    @Test
    void 기존_주문은_상품가격으로_가격스냅샷을_채운다() throws SQLException {
        try (Connection connection = connection()) {
            connection.createStatement().execute("""
                    CREATE TABLE members (
                        id BIGINT PRIMARY KEY,
                        nickname VARCHAR(30) NOT NULL,
                        role VARCHAR(20) NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE products (
                        id BIGINT PRIMARY KEY,
                        seller_id BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
                        price BIGINT NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        product_id BIGINT NOT NULL
                    )
                    """);
            connection.createStatement().execute("INSERT INTO members (id, nickname, role) VALUES (1, '판매자', 'MEMBER')");
            connection.createStatement().execute("INSERT INTO products (id, seller_id, price) VALUES (10, 1, 15000)");
            connection.createStatement().execute("INSERT INTO orders (id, product_id) VALUES (100, 10)");
        }

        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connection()) {
            assertThat(queryLong(connection, "SELECT list_price FROM orders WHERE id = 100")).isEqualTo(15_000L);
            assertThat(queryLong(connection, "SELECT promotion_discount_amount FROM orders WHERE id = 100")).isZero();
            assertThat(queryLong(connection, "SELECT final_price FROM orders WHERE id = 100")).isEqualTo(15_000L);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }

    private long queryLong(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
