package com.sweet.market.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("market_test")
            .withUsername("market")
            .withPassword("market")
            .withInitScript("org/springframework/batch/core/schema-postgresql.sql");

    static {
        POSTGRESQL.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
        registry.add("jwt.secret", () -> "sweet-market-test-secret-key-32bytes-minimum");
        registry.add("jwt.access-token-validity-seconds", () -> "3600");
        registry.add("product.images.upload-root", () -> "build/test-product-images");
        registry.add("product.images.temp-expiration", () -> "60m");
        registry.add("spring.servlet.multipart.max-file-size", () -> "5MB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "6MB");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE settlements, deliveries, refund_requests, payments, reviews, orders, cart_items, wishlist_items, product_image_uploads, product_images, products, members RESTART IDENTITY CASCADE");
        deleteTestProductImages();
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
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
}
