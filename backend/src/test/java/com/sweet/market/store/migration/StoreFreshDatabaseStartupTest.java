package com.sweet.market.store.migration;

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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "market.scheduling.enabled=false",
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");
        assertThat(tableExists("stores")).isTrue();
        assertThat(tableExists("members")).isTrue();
        assertThat(tableExists("products")).isTrue();
        assertThat(tableExists("orders")).isTrue();
        assertThat(tableExists("promotion_campaigns")).isTrue();
        assertThat(tableExists("promotion_targets")).isTrue();
        assertThat(tableExists("purchase_requests")).isTrue();
        assertThat(tableExists("product_view_events")).isTrue();
        assertThat(tableExists("product_view_deduplications")).isTrue();
        assertThat(columnExists("coupon_campaigns", "issue_limit")).isTrue();
        assertThat(columnIsNotNull("coupon_campaigns", "issued_count")).isTrue();
        assertThat(checkConstraintDefinition("chk_coupon_campaigns_issue_limit"))
                .contains("issue_limit IS NULL", "issue_limit > 0");
        assertThat(checkConstraintDefinition("chk_coupon_campaigns_issued_count"))
                .contains("issued_count >= 0", "issued_count <= issue_limit");
        assertThat(columnExists("products", "store_id")).isTrue();
        assertThat(columnIsNotNull("products", "category")).isTrue();
        assertThat(checkConstraintDefinition("chk_products_category"))
                .contains("COMPUTERS", "MOBILE", "HOME_APPLIANCES", "VEHICLES", "LIVING_HOBBY", "OTHER");
        assertThat(columnExists("orders", "seller_id")).isTrue();
        assertThat(indexExists("idx_products_store_status_id")).isTrue();
        assertThat(indexExists("idx_products_store_status_price_id")).isTrue();
        assertThat(indexExists("idx_products_title_trgm")).isTrue();
        assertThat(indexExists("idx_products_description_trgm")).isTrue();
        assertThat(indexExists("idx_product_images_product_representative_sort_order_id")).isTrue();
        assertThat(indexExists("idx_orders_seller_id")).isTrue();
        assertThat(indexExists("idx_purchase_requests_expiry")).isTrue();
        assertThat(indexExists("idx_purchase_requests_processing_lease")).isTrue();
        assertThat(indexExists("idx_product_view_events_product_viewed_at")).isTrue();
        assertThat(checkConstraintDefinition("chk_purchase_requests_status"))
                .contains("PROCESSING", "COMPLETED");
        assertThat(foreignKeyExists("products", "store_id", "stores", "id")).isTrue();
        assertThat(foreignKeyExists("orders", "seller_id", "members", "id")).isTrue();
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

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count == 1;
    }

    private boolean columnIsNotNull(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND column_name = ?
                          AND is_nullable = 'NO'
                        """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count == 1;
    }

    private String checkConstraintDefinition(String constraintName) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE((SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?), '')",
                String.class,
                constraintName
        );
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                Integer.class,
                indexName
        );
        return count != null && count == 1;
    }

    private boolean foreignKeyExists(String tableName, String columnName, String referencedTable, String referencedColumn) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
                        WHERE tc.constraint_type = 'FOREIGN KEY'
                          AND tc.table_schema = 'public'
                          AND tc.table_name = ? AND kcu.column_name = ?
                          AND ccu.table_name = ? AND ccu.column_name = ?
                        """,
                Integer.class, tableName, columnName, referencedTable, referencedColumn
        );
        return count != null && count == 1;
    }
}
