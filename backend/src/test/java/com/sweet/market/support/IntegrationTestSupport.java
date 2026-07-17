package com.sweet.market.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(IntegrationTestSupport.OperationalEventOutboxTestConfiguration.class)
public abstract class IntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("market_test")
            .withUsername("market")
            .withPassword("market")
            .withInitScript("org/springframework/batch/core/schema-postgresql.sql");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    static {
        POSTGRESQL.start();
        REDIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("jwt.secret", () -> "sweet-market-test-secret-key-32bytes-minimum");
        registry.add("jwt.access-token-validity-seconds", () -> "3600");
        registry.add("product.images.upload-root", () -> "build/test-product-images");
        registry.add("product.images.temp-expiration", () -> "60m");
        registry.add("spring.servlet.multipart.max-file-size", () -> "5MB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "6MB");
        registry.add("market.operations-projector.enabled", () -> "false");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE operational_event_outbox, purchase_requests, member_coupons, coupon_campaign_targets, coupon_campaigns, store_memberships, stores, settlements, deliveries, refund_requests, payments, reviews, orders, cart_items, wishlist_items, product_image_uploads, product_images, products, members RESTART IDENTITY CASCADE");
        stringRedisTemplate.keys("coupon:issue:*").forEach(stringRedisTemplate::delete);
        deleteTestProductImages();
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected Long activePersonalStoreId(String accessToken) throws Exception {
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/stores/me")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").get(0).path("storeId").asLong();
    }

    private void deleteTestProductImages() {
        Path uploadRoot = Path.of("build/test-product-images");
        if (!Files.exists(uploadRoot)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(uploadRoot)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(this::deleteIfExists);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OperationalEventOutboxTestConfiguration {

        @Bean
        InitializingBean operationalEventOutbox(JdbcTemplate jdbcTemplate) {
            return () -> jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS operational_event_outbox (
                        id BIGSERIAL PRIMARY KEY,
                        event_id UUID NOT NULL UNIQUE,
                        event_type VARCHAR(80) NOT NULL,
                        schema_version INTEGER NOT NULL,
                        aggregate_type VARCHAR(40) NOT NULL,
                        aggregate_id BIGINT,
                        aggregate_version BIGINT,
                        store_id BIGINT,
                        campaign_id BIGINT,
                        partition_key VARCHAR(160) NOT NULL,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        payload JSONB NOT NULL,
                        delivery_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        attempt_count INTEGER NOT NULL DEFAULT 0,
                        next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_error VARCHAR(1000),
                        processed_at TIMESTAMPTZ,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }
}
