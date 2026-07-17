package com.sweet.market.operations.admin;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOperationsDashboardApiTest extends IntegrationTestSupport {

    private static final Instant BUCKET = Instant.parse("2026-07-11T01:00:00Z");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMembershipRepository storeMembershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    private long activeGenerationId;

    @BeforeEach
    void projection_테이블을_초기화한다() {
        createProjectionTables();
        jdbcTemplate.execute("TRUNCATE TABLE projection_generations RESTART IDENTITY CASCADE");
        activeGenerationId = insertGeneration("ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO projection_event_receipts (
                    generation_id, projection_name, event_id, processed_at
                ) VALUES (?, 'admin-dashboard-test', ?, ?)
                """, activeGenerationId, UUID.randomUUID(), timestamp(Instant.parse("2026-07-13T00:05:00Z")));
    }

    @Test
    void ADMIN은_플랫폼과_상점별_운영요약을_조회한다() throws Exception {
        Member admin = saveAdmin("operations-admin@example.com");
        Store firstStore = saveBusinessStore("first-owner@example.com", "첫 상점");
        Store secondStore = saveBusinessStore("second-owner@example.com", "둘째 상점");
        insertStoreMetric(firstStore.getId(), "PAYMENT_FAILED", 3, 2, 100, 80, 1);
        insertStoreMetric(secondStore.getId(), "SOLD_OUT", 7, 4, 300, 200, 2);
        insertCampaignMetric(firstStore.getId(), 31L, "STORE", firstStore.getId(), "EXHAUSTED", 5, 2, 4);
        insertCampaignMetric(secondStore.getId(), 32L, "STORE", secondStore.getId(), "INELIGIBLE", 8, 3, 6);
        insertInventory(firstStore.getId(), 101L, true, 2);
        insertInventory(secondStore.getId(), 102L, true, 1);
        insertAudit(firstStore.getId(), 31L, "COUPON", "PAUSE");
        insertAudit(secondStore.getId(), 32L, "COUPON", "END");

        String platformResponse = mockMvc.perform(period(get("/api/admin/operations-dashboard"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderSuccessCount").value(10))
                .andExpect(jsonPath("$.data.purchaseFailureCount").value(6))
                .andExpect(jsonPath("$.data.claimSuccessCount").value(13))
                .andExpect(jsonPath("$.data.redemptionSuccessCount").value(10))
                .andExpect(jsonPath("$.data.promotionDiscounts.applied").value(400))
                .andExpect(jsonPath("$.data.promotionDiscounts.realized").value(280))
                .andExpect(jsonPath("$.data.lowStockCount").value(2))
                .andExpect(jsonPath("$.data.auditCount").value(2))
                .andExpect(jsonPath("$.data.health.pendingCount").value(0))
                .andExpect(jsonPath("$.data.health.retryCount").value(0))
                .andExpect(jsonPath("$.data.health.deadCount").value(0))
                .andReturn().getResponse().getContentAsString();

        String firstStoreResponse = mockMvc.perform(period(get("/api/admin/operations-dashboard"))
                        .queryParam("storeId", firstStore.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(firstStore.getId()))
                .andExpect(jsonPath("$.data.orderSuccessCount").value(3))
                .andExpect(jsonPath("$.data.purchaseFailureCount").value(2))
                .andExpect(jsonPath("$.data.claimSuccessCount").value(5))
                .andExpect(jsonPath("$.data.redemptionSuccessCount").value(4))
                .andExpect(jsonPath("$.data.promotionDiscounts.applied").value(100))
                .andExpect(jsonPath("$.data.lowStockCount").value(1))
                .andExpect(jsonPath("$.data.auditCount").value(1))
                .andReturn().getResponse().getContentAsString();
        String secondStoreResponse = mockMvc.perform(period(get("/api/admin/operations-dashboard"))
                        .queryParam("storeId", secondStore.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long platformOrders = objectMapper.readTree(platformResponse).path("data").path("orderSuccessCount").asLong();
        long firstStoreOrders = objectMapper.readTree(firstStoreResponse).path("data").path("orderSuccessCount").asLong();
        long secondStoreOrders = objectMapper.readTree(secondStoreResponse).path("data").path("orderSuccessCount").asLong();
        assertThat(platformOrders).isEqualTo(firstStoreOrders + secondStoreOrders);
    }

    @Test
    void ADMIN은_상점캠페인을_조회하지만_상점소유자로_변경하지_않는다() throws Exception {
        Member admin = saveAdmin("campaign-inspection-admin@example.com");
        Store store = saveBusinessStore("campaign-owner@example.com", "캠페인 상점");
        long campaignId = insertCouponCampaign(store.getId(), "PAUSED");
        insertCampaignMetric(store.getId(), campaignId, "STORE", store.getId(), "EXHAUSTED", 1, 1, 0);

        mockMvc.perform(period(get("/api/admin/operations-dashboard/campaigns"))
                        .queryParam("storeId", store.getId().toString())
                        .queryParam("ownerType", "store")
                        .queryParam("campaignKind", "coupon")
                        .queryParam("campaignStatus", "paused")
                        .queryParam("size", "1000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].campaignId").value(campaignId))
                .andExpect(jsonPath("$.data.content[0].ownerType").value("STORE"))
                .andExpect(jsonPath("$.data.content[0].ownerStoreId").value(store.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("PAUSED"));

        mockMvc.perform(post("/api/admin/operations-dashboard/campaigns/{campaignId}/pause", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 일반회원은_관리자_운영API에_접근할_수_없다() throws Exception {
        Member member = saveMember("operations-member@example.com");

        mockMvc.perform(get("/api/admin/operations-dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void ADMIN은_적용되는_차원으로_운영_드릴다운을_필터링한다() throws Exception {
        Member admin = saveAdmin("operations-filter-admin@example.com");
        Store store = saveBusinessStore("filter-owner@example.com", "필터 상점");
        long campaignId = insertCouponCampaign(store.getId(), "PAUSED");
        insertCampaignMetric(store.getId(), campaignId, "STORE", store.getId(), "EXHAUSTED", 1, 2, 0);
        insertStoreMetric(store.getId(), "PAYMENT_FAILED", 0, 3, 0, 0, 0);
        insertInventory(store.getId(), 301L, true, 4);
        insertAudit(store.getId(), campaignId, "COUPON", "PAUSE");

        mockMvc.perform(period(get("/api/admin/operations-dashboard/outcomes"))
                        .queryParam("storeId", store.getId().toString())
                        .queryParam("ownerType", "STORE")
                        .queryParam("campaignKind", "COUPON")
                        .queryParam("reason", "EXHAUSTED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].outcomeType").value("CAMPAIGN"))
                .andExpect(jsonPath("$.data.content[0].reason").value("EXHAUSTED"));

        mockMvc.perform(get("/api/admin/operations-dashboard/inventory-pressure")
                        .queryParam("storeId", store.getId().toString())
                        .queryParam("productId", "301")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value(301));

        mockMvc.perform(period(get("/api/admin/operations-dashboard/audits"))
                        .queryParam("storeId", store.getId().toString())
                        .queryParam("ownerType", "STORE")
                        .queryParam("campaignKind", "COUPON")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].campaignId").value(campaignId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder period(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.queryParam("from", "2026-07-10").queryParam("to", "2026-07-12");
    }

    private Member saveAdmin(String email) {
        return memberRepository.save(Member.createAdmin(email, passwordEncoder.encode("password123"), "관리자"));
    }

    private Member saveMember(String email) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), "회원"));
    }

    private Store saveBusinessStore(String ownerEmail, String publicName) {
        Member owner = saveMember(ownerEmail);
        Store store = Store.applyBusiness(owner, publicName, "소개", "법인", UUID.randomUUID().toString().substring(0, 12));
        store.approve();
        store = storeRepository.save(store);
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        return store;
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
    }

    private long insertGeneration(String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, bootstrap_high_water_id, activated_at
                ) VALUES (?, '2026-07-13T00:00:00Z', '2026-05-01T00:00:00Z', 0, '2026-07-13T00:00:00Z')
                RETURNING id
                """, Long.class, status);
    }

    private void insertStoreMetric(
            long storeId, String reason, long orders, long failures,
            long promotionApplied, long promotionRealized, long soldOut
    ) {
        jdbcTemplate.update("""
                INSERT INTO store_metric_hourly (
                    generation_id, bucket_start, store_id, outcome_reason,
                    order_success_count, purchase_failure_count,
                    promotion_applied_amount, promotion_realized_amount, sold_out_transition_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, activeGenerationId, timestamp(BUCKET), storeId, reason, orders, failures,
                promotionApplied, promotionRealized, soldOut);
    }

    private void insertCampaignMetric(
            long commerceStoreId, long campaignId, String ownerType, long ownerStoreId,
            String reason, long claims, long claimFailures, long redemptions
    ) {
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason,
                    claim_success_count, claim_failure_count, redemption_success_count
                ) VALUES (?, ?, ?, 'COUPON', ?, ?, ?, ?, ?, ?, ?)
                """, activeGenerationId, timestamp(BUCKET), commerceStoreId, campaignId,
                ownerType, ownerStoreId, reason, claims, claimFailures, redemptions);
    }

    private void insertInventory(long storeId, long productId, boolean lowStock, long failures) {
        jdbcTemplate.update("""
                INSERT INTO inventory_pressure_projection (
                    generation_id, product_id, store_id, sales_policy, available_quantity,
                    low_stock, recent_reservation_failure_count, last_reservation_failure_at,
                    aggregate_version, updated_at
                ) VALUES (?, ?, ?, 'STOCK_MANAGED', 3, ?, ?, ?, 1, ?)
                """, activeGenerationId, productId, storeId, lowStock, failures,
                timestamp(BUCKET), timestamp(BUCKET));
        jdbcTemplate.update("""
                INSERT INTO inventory_failure_hourly (
                    generation_id, bucket_start, product_id, store_id, failure_count
                ) VALUES (?, ?, ?, ?, ?)
                """, activeGenerationId, timestamp(BUCKET), productId, storeId, failures);
    }

    private void insertAudit(long storeId, long campaignId, String kind, String command) {
        jdbcTemplate.update("""
                INSERT INTO campaign_audit_projection (
                    generation_id, event_id, campaign_kind, campaign_id,
                    owner_type, owner_store_id, actor_member_id, command,
                    occurred_at, aggregate_version, before_summary, after_summary
                ) VALUES (?, ?, ?, ?, 'STORE', ?, 1, ?, ?, 1, NULL, '{}'::jsonb)
                """, activeGenerationId, UUID.randomUUID(), kind, campaignId,
                storeId, command, timestamp(BUCKET));
    }

    private long insertCouponCampaign(long storeId, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO coupon_campaigns (
                    owner_type, store_id, scope, discount_type, discount_value,
                    minimum_purchase_amount, stackable, title,
                    issue_starts_at, issue_ends_at, validity_type, validity_days,
                    version, issued_count, lifecycle_status, created_at, updated_at
                ) VALUES (
                    'STORE', ?, 'ALL_PRODUCTS', 'FIXED_AMOUNT', 100,
                    0, FALSE, '관리자 조회 쿠폰', '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z',
                    'DAYS_FROM_ISSUANCE', 7, 0, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) RETURNING id
                """, Long.class, storeId, status);
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private void createProjectionTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_generations (
                    id BIGSERIAL PRIMARY KEY, status VARCHAR(20) NOT NULL,
                    cutoff_at TIMESTAMPTZ NOT NULL, tracking_started_at TIMESTAMPTZ NOT NULL,
                    bootstrap_high_water_id BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    activated_at TIMESTAMPTZ, retired_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_event_receipts (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    projection_name VARCHAR(80) NOT NULL, event_id UUID NOT NULL,
                    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (generation_id, projection_name, event_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS store_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, store_id BIGINT NOT NULL,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    order_success_count BIGINT NOT NULL DEFAULT 0,
                    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
                    reservation_failure_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    sold_out_transition_count BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (generation_id, bucket_start, store_id, outcome_reason)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_metric_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, commerce_store_id BIGINT NOT NULL DEFAULT 0,
                    campaign_kind VARCHAR(20) NOT NULL, campaign_id BIGINT NOT NULL,
                    campaign_owner_type VARCHAR(20) NOT NULL,
                    campaign_owner_store_id BIGINT NOT NULL DEFAULT 0,
                    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
                    claim_success_count BIGINT NOT NULL DEFAULT 0,
                    claim_failure_count BIGINT NOT NULL DEFAULT 0,
                    redemption_success_count BIGINT NOT NULL DEFAULT 0,
                    redemption_failure_count BIGINT NOT NULL DEFAULT 0,
                    order_success_count BIGINT NOT NULL DEFAULT 0,
                    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
                    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
                    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (generation_id, bucket_start, commerce_store_id, campaign_kind,
                        campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS inventory_pressure_projection (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    product_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
                    sales_policy VARCHAR(20) NOT NULL, available_quantity INTEGER,
                    low_stock BOOLEAN NOT NULL, last_sold_out_at TIMESTAMPTZ,
                    recent_reservation_failure_count BIGINT NOT NULL DEFAULT 0,
                    last_reservation_failure_at TIMESTAMPTZ, aggregate_version BIGINT NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (generation_id, product_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS inventory_failure_hourly (
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    bucket_start TIMESTAMPTZ NOT NULL, product_id BIGINT NOT NULL,
                    store_id BIGINT NOT NULL, failure_count BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (generation_id, bucket_start, product_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_audit_projection (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    event_id UUID NOT NULL, campaign_kind VARCHAR(20) NOT NULL,
                    campaign_id BIGINT NOT NULL, owner_type VARCHAR(20) NOT NULL,
                    owner_store_id BIGINT NOT NULL DEFAULT 0, actor_member_id BIGINT NOT NULL,
                    command VARCHAR(20) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
                    aggregate_version BIGINT, before_summary JSONB, after_summary JSONB NOT NULL,
                    UNIQUE (generation_id, event_id)
                )
                """);
    }
}
