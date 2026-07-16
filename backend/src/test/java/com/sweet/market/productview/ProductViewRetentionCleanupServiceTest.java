package com.sweet.market.productview;

import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.productview.application.ProductViewRetentionCleanupService;
import com.sweet.market.productview.repository.ProductViewDeduplicationRepository;
import com.sweet.market.productview.repository.ProductViewEventRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductViewRetentionCleanupServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Autowired
    private ProductViewEventRepository productViewEventRepository;

    @Autowired
    private ProductViewDeduplicationRepository productViewDeduplicationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void 조회_이벤트_픽스처를_초기화한다() {
        jdbcTemplate.execute("TRUNCATE TABLE product_view_events, product_view_deduplications RESTART IDENTITY CASCADE");
    }

    @Test
    void 칠일을_지난_조회이벤트와_중복제거_행을_삭제한다() throws Exception {
        String email = "view-retention@example.com";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, "password123", "보존정책"))))
                .andExpect(status().isCreated());
        Long storeId = jdbcTemplate.queryForObject(
                "select id from stores where owner_member_id = (select id from members where email = ?)", Long.class, email);
        Long productId = jdbcTemplate.queryForObject("""
                insert into products (version, store_id, title, description, price, category, status, sales_policy)
                values (0, ?, '조회 상품', '보존 정책 테스트 상품', 1000, 'OTHER', 'ON_SALE', 'SINGLE_ITEM')
                returning id
                """, Long.class, storeId);
        Instant cutoff = NOW.minusSeconds(7 * 24 * 60 * 60);

        jdbcTemplate.update("insert into product_view_events (product_id, visitor_hash, viewed_at) values (?, ?, ?)",
                productId, "a".repeat(64), Timestamp.from(cutoff.minusSeconds(1)));
        jdbcTemplate.update("insert into product_view_events (product_id, visitor_hash, viewed_at) values (?, ?, ?)",
                productId, "b".repeat(64), Timestamp.from(cutoff));
        jdbcTemplate.update("insert into product_view_deduplications (product_id, visitor_hash, last_counted_at) values (?, ?, ?)",
                productId, "c".repeat(64), Timestamp.from(cutoff.minusSeconds(1)));
        jdbcTemplate.update("insert into product_view_deduplications (product_id, visitor_hash, last_counted_at) values (?, ?, ?)",
                productId, "d".repeat(64), Timestamp.from(cutoff));

        ProductViewRetentionCleanupService cleanupService = new ProductViewRetentionCleanupService(
                productViewEventRepository,
                productViewDeduplicationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        transactionTemplate.executeWithoutResult(status -> cleanupService.cleanup());

        assertThat(count("product_view_events", productId)).isEqualTo(1);
        assertThat(count("product_view_deduplications", productId)).isEqualTo(1);
    }

    private long count(String table, Long productId) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where product_id = ?", Long.class, productId);
    }
}
