package com.sweet.market.operations.coupon;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.operations.projection.OperationalProjectionCoordinator;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class CouponOutcomeProjectionTest extends IntegrationTestSupport {

    @Autowired
    private OperationalProjectionCoordinator coordinator;

    @BeforeEach
    void 쿠폰_성과_projection_테이블을_준비한다() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_generations (
                    id BIGSERIAL PRIMARY KEY,
                    status VARCHAR(20) NOT NULL,
                    cutoff_at TIMESTAMPTZ NOT NULL,
                    tracking_started_at TIMESTAMPTZ NOT NULL,
                    bootstrap_high_water_id BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    activated_at TIMESTAMPTZ,
                    retired_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_event_receipts (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    projection_name VARCHAR(80) NOT NULL,
                    event_id UUID NOT NULL,
                    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_coupon_outcome_test_receipt
                        UNIQUE (generation_id, projection_name, event_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL,
                    commerce_store_id BIGINT NOT NULL DEFAULT 0,
                    campaign_kind VARCHAR(20) NOT NULL,
                    campaign_id BIGINT NOT NULL,
                    campaign_owner_type VARCHAR(20) NOT NULL,
                    campaign_owner_store_id BIGINT NOT NULL DEFAULT 0,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    claim_success_count BIGINT NOT NULL DEFAULT 0,
                    claim_failure_count BIGINT NOT NULL DEFAULT 0,
                    redemption_success_count BIGINT NOT NULL DEFAULT 0,
                    redemption_failure_count BIGINT NOT NULL DEFAULT 0,
                    order_success_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (
                        generation_id, bucket_start, commerce_store_id, campaign_kind,
                        campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
                    )
                )
                """);
        jdbcTemplate.execute("""
                TRUNCATE TABLE campaign_metric_hourly, projection_event_receipts,
                               operational_event_outbox, projection_generations RESTART IDENTITY CASCADE
                """);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, activated_at
                ) VALUES ('ACTIVE', ?, ?, ?)
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    @Test
    void 새_쿠폰_발급_성공은_원본과_같은_트랜잭션에서_한번_집계한다() throws Exception {
        String token = signupAndLogin("coupon-outcome-new@example.com", "신규 발급자");
        long campaignId = campaign(null, "SCHEDULED");

        claim(token, campaignId)
                .andExpect(status().isOk());

        assertThat(memberCouponCount(campaignId)).isOne();
        assertThat(issuedCount(campaignId)).isOne();
        projectWithDuplicateDelivery();
        assertThat(metric(campaignId, "NONE", "claim_success_count")).isOne();
    }

    @Test
    void 이미_발급된_쿠폰_재요청은_ALREADY_CLAIMED로_집계한다() throws Exception {
        String token = signupAndLogin("coupon-outcome-duplicate@example.com", "중복 발급자");
        long campaignId = campaign(null, "SCHEDULED");
        claim(token, campaignId).andExpect(status().isOk());
        long couponId = memberCouponId(campaignId);
        clearOutcomeEvents();

        claim(token, campaignId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(couponId));

        project();
        assertThat(memberCouponCount(campaignId)).isOne();
        assertThat(metric(campaignId, "ALREADY_CLAIMED", "claim_failure_count")).isOne();
    }

    @Test
    void 발급한도_소진은_EXHAUSTED로_집계한다() throws Exception {
        String firstToken = signupAndLogin("coupon-outcome-capacity-first@example.com", "선착순 발급자");
        String lateToken = signupAndLogin("coupon-outcome-capacity-late@example.com", "후순위 발급자");
        long campaignId = campaign(1, "SCHEDULED");
        claim(firstToken, campaignId).andExpect(status().isOk());
        clearOutcomeEvents();

        claim(lateToken, campaignId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ISSUE_LIMIT_EXCEEDED"));

        project();
        assertThat(metric(campaignId, "EXHAUSTED", "claim_failure_count")).isOne();
    }

    @Test
    void 비활성_캠페인은_INACTIVE로_집계한다() throws Exception {
        String token = signupAndLogin("coupon-outcome-inactive@example.com", "비활성 발급자");
        long campaignId = campaign(null, "PAUSED");

        claim(token, campaignId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_LIFECYCLE_NOT_ALLOWED"));

        project();
        assertThat(metric(campaignId, "INACTIVE", "claim_failure_count")).isOne();
    }

    @Test
    void 주문이_커밋되기_전_쿠폰_예약은_사용_성공으로_집계하지_않는다() throws Exception {
        RedemptionFixture fixture = redemptionFixture("coupon-outcome-reserved");

        long orderId = reserveOrder(fixture);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM coupon_reservations WHERE order_id = ?", String.class, orderId
        )).isEqualTo("RESERVED");
        project();
        assertThat(metric(fixture.campaignId(), "NONE", "redemption_success_count")).isZero();
    }

    @Test
    void 주문과_쿠폰사용이_커밋되면_사용_성공을_한번_집계한다() throws Exception {
        RedemptionFixture fixture = redemptionFixture("coupon-outcome-consumed");
        long orderId = reserveOrder(fixture);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.buyerToken())))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM member_coupons WHERE id = ?", String.class, fixture.memberCouponId()
        )).isEqualTo("USED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId
        )).isEqualTo("PAID");
        projectWithDuplicateDelivery();
        assertThat(metric(fixture.campaignId(), "NONE", "redemption_success_count")).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT commerce_store_id FROM campaign_metric_hourly
                WHERE campaign_id = ? AND outcome_reason = 'NONE'
                """, Long.class, fixture.campaignId())).isEqualTo(fixture.commerceStoreId());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT campaign_owner_type || ':' || campaign_owner_store_id
                FROM campaign_metric_hourly
                WHERE campaign_id = ? AND outcome_reason = 'NONE'
                """, String.class, fixture.campaignId())).isEqualTo("PLATFORM:0");
    }

    private org.springframework.test.web.servlet.ResultActions claim(String token, long campaignId) throws Exception {
        return mockMvc.perform(post("/api/coupon-campaigns/{campaignId}/claim", campaignId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long campaign(Integer issueLimit, String lifecycleStatus) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO coupon_campaigns (
                    version, owner_type, scope, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, stackable, title,
                    issue_starts_at, issue_ends_at, validity_type, validity_days,
                    lifecycle_status, issued_count, issue_limit, created_at, updated_at
                ) VALUES (
                    0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1000,
                    NULL, 0, TRUE, '성과 집계 쿠폰',
                    current_timestamp - interval '1 day', current_timestamp + interval '1 day',
                    'DAYS_FROM_ISSUANCE', 7, ?, 0, ?, current_timestamp, current_timestamp
                ) RETURNING id
                """, Long.class, lifecycleStatus, issueLimit);
    }

    private RedemptionFixture redemptionFixture(String prefix) throws Exception {
        String sellerToken = signupAndLogin(prefix + "-seller@example.com", "판매자");
        String buyerEmail = prefix + "-buyer@example.com";
        String buyerToken = signupAndLogin(buyerEmail, "구매자");
        long commerceStoreId = activePersonalStoreId(sellerToken);
        long productId = createProduct(sellerToken, commerceStoreId);
        long campaignId = campaign(null, "ENDED");
        long memberCouponId = jdbcTemplate.queryForObject("""
                INSERT INTO member_coupons (
                    member_id, coupon_campaign_id, issued_at, valid_until,
                    discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, scope, stackable, status
                ) VALUES (
                    ?, ?, current_timestamp, current_timestamp + interval '7 days',
                    'FIXED_AMOUNT', 1000, NULL, 0, 'ALL_PRODUCTS', TRUE, 'ISSUED'
                ) RETURNING id
                """, Long.class, memberId(buyerEmail), campaignId);
        clearOutcomeEvents();
        return new RedemptionFixture(
                buyerToken, campaignId, memberCouponId, productId, commerceStoreId);
    }

    private long reserveOrder(RedemptionFixture fixture) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.buyerToken()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(
                                fixture.productId(), fixture.memberCouponId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private long createProduct(String sellerToken, long storeId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "coupon-outcome.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        String uploadResponse = mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long uploadId = objectMapper.readTree(uploadResponse).path("data").path("id").asLong();
        String productResponse = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "쿠폰 성과 상품",
                                  "description": "쿠폰 성과 집계용 상품",
                                  "price": 10000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [{
                                    "uploadId": %d,
                                    "sortOrder": 0,
                                    "representative": true
                                  }]
                                }
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(productResponse).path("data").path("id").asLong();
    }

    private String signupAndLogin(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, "password123", nickname))))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private void project() {
        coordinator.projectNextBatch(Instant.now().plusSeconds(2), 100);
    }

    private void projectWithDuplicateDelivery() {
        Instant projectionTime = Instant.now().plusSeconds(2);
        coordinator.projectNextBatch(projectionTime, 100);
        jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = 'RETRY', next_attempt_at = ?
                WHERE event_type IN ('COUPON_CLAIM_OUTCOME', 'COUPON_REDEMPTION_OUTCOME')
                """, Timestamp.from(projectionTime));
        coordinator.projectNextBatch(projectionTime, 100);
    }

    private void clearOutcomeEvents() {
        jdbcTemplate.execute("TRUNCATE TABLE operational_event_outbox RESTART IDENTITY CASCADE");
    }

    private long metric(long campaignId, String reason, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(%s), 0) FROM campaign_metric_hourly
                WHERE campaign_id = ? AND outcome_reason = ?
                """.formatted(column), Long.class, campaignId, reason);
    }

    private long memberCouponCount(long campaignId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_coupons WHERE coupon_campaign_id = ?",
                Long.class, campaignId);
    }

    private long memberCouponId(long campaignId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM member_coupons WHERE coupon_campaign_id = ?",
                Long.class, campaignId);
    }

    private int issuedCount(long campaignId) {
        return jdbcTemplate.queryForObject(
                "SELECT issued_count FROM coupon_campaigns WHERE id = ?",
                Integer.class, campaignId);
    }

    private long memberId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RedemptionFixture(
            String buyerToken,
            long campaignId,
            long memberCouponId,
            long productId,
            long commerceStoreId
    ) {
    }
}
