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
class StoreMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("store_migration_test")
            .withUsername("market")
            .withPassword("market");

    @Test
    void 기존_회원과_상품_주문_데이터를_상점_구조로_이관한다() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        )) {
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
                        seller_id BIGINT NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        product_id BIGINT NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    INSERT INTO members (id, nickname, role) VALUES (1, '판매자', 'MEMBER')
                    """);
            connection.createStatement().execute("INSERT INTO products (id, seller_id) VALUES (10, 1)");
            connection.createStatement().execute("INSERT INTO orders (id, product_id) VALUES (100, 10)");
        }

        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        )) {
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM stores WHERE owner_member_id = 1 AND type = 'PERSONAL'"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM store_memberships WHERE member_id = 1 AND role = 'OWNER' AND active"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM products WHERE id = 10 AND store_id IS NOT NULL"))
                    .isEqualTo(1);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM orders WHERE id = 100 AND seller_id = 1"))
                    .isEqualTo(1);
        }
    }

    private long queryLong(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
