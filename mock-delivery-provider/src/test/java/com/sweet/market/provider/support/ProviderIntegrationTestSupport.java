package com.sweet.market.provider.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class ProviderIntegrationTestSupport {

    @Container
    protected static final PostgreSQLContainer<?> postgreSQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("delivery_provider")
            .withUsername("delivery_provider")
            .withPassword("delivery_provider");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQL::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQL::getUsername);
        registry.add("spring.datasource.password", postgreSQL::getPassword);
        registry.add("provider.integration-security.allowed-clock-skew", () -> "PT5M");
        registry.add("provider.integration-security.max-body-bytes", () -> "1048576");
        registry.add("provider.integration-security.replay-retention", () -> "PT10M");
        registry.add("provider.integration-security.cleanup-batch-size", () -> "1000");
        registry.add("provider.integration-security.clients[0].client-id", () -> "sweet-market");
        registry.add("provider.integration-security.clients[0].api-key", () -> "provider-api-key");
        registry.add("provider.integration-security.clients[0].keys[0].key-id",
                () -> "current-key");
        registry.add("provider.integration-security.clients[0].keys[0].secret",
                () -> "current-secret-32-bytes-minimum-value");
        registry.add("provider.integration-security.clients[0].keys[1].key-id",
                () -> "next-key");
        registry.add("provider.integration-security.clients[0].keys[1].secret",
                () -> "next-secret-32-bytes-minimum-value");
    }
}
