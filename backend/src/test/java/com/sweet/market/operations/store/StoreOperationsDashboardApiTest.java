package com.sweet.market.operations.store;

import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.operations.api.DashboardPeriod;
import com.sweet.market.operations.api.DashboardPeriodResolver;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoreOperationsDashboardApiTest extends IntegrationTestSupport {

    private static final String FROM = "2026-07-10";
    private static final String TO = "2026-07-12";
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

    @MockitoSpyBean
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private long activeGenerationId;
    private long retiredGenerationId;

    @BeforeEach
    void projectionFixtures() {
        createProjectionTables();
        jdbcTemplate.execute("TRUNCATE TABLE projection_generations RESTART IDENTITY CASCADE");
        retiredGenerationId = insertGeneration("RETIRED", Instant.parse("2026-07-01T00:00:00Z"));
        activeGenerationId = insertGeneration("ACTIVE", Instant.parse("2026-07-13T00:00:00Z"));
        jdbcTemplate.update("""
                INSERT INTO projection_event_receipts (
                    generation_id, projection_name, event_id, processed_at
                ) VALUES (?, 'dashboard-test', ?, ?)
                """, activeGenerationId, UUID.randomUUID(), timestamp(Instant.parse("2026-07-13T00:05:00Z")));
    }

    @Test
    void OWNER는_자기_상점_운영_요약을_조회한다() throws Exception {
        Member owner = saveMember("dashboard-owner@example.com", "소유자");
        Store store = saveBusinessStore(owner, "운영 지표 상점");
        insertStoreMetric(activeGenerationId, store.getId(), BUCKET, "SOLD_OUT",
                3, 2, 100, 80, 20, 10, 40, 30, 10, 5, 1);
        insertCampaignMetric(activeGenerationId, store.getId(), "COUPON", 31L,
                "STORE", store.getId(), "NONE", 5, 0, 4, 0, 0, 0, 0);
        insertCampaignMetric(activeGenerationId, store.getId(), "COUPON", 31L,
                "STORE", store.getId(), "EXHAUSTED", 0, 2, 0, 0, 0, 0, 0);
        insertInventory(activeGenerationId, store.getId(), 101L, true, 2);
        insertStoreMetric(retiredGenerationId, store.getId(), BUCKET, "NONE",
                999, 999, 999, 999, 999, 999, 999, 999, 999, 999, 999);

        mockMvc.perform(period(get("/api/stores/{storeId}/operations/dashboard", store.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(store.getId()))
                .andExpect(jsonPath("$.data.storeName").value("운영 지표 상점"))
                .andExpect(jsonPath("$.data.period.from").value(FROM))
                .andExpect(jsonPath("$.data.period.to").value(TO))
                .andExpect(jsonPath("$.data.period.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.projectionUpdatedAt").value("2026-07-13T00:05:00Z"))
                .andExpect(jsonPath("$.data.trackingStartedAt").value("2026-05-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.claimSuccessCount").value(5))
                .andExpect(jsonPath("$.data.redemptionSuccessCount").value(4))
                .andExpect(jsonPath("$.data.orderSuccessCount").value(3))
                .andExpect(jsonPath("$.data.purchaseFailureCount").value(2))
                .andExpect(jsonPath("$.data.promotionDiscounts.applied").value(100))
                .andExpect(jsonPath("$.data.promotionDiscounts.realized").value(80))
                .andExpect(jsonPath("$.data.promotionDiscounts.canceled").value(20))
                .andExpect(jsonPath("$.data.promotionDiscounts.refunded").value(10))
                .andExpect(jsonPath("$.data.couponDiscounts.applied").value(40))
                .andExpect(jsonPath("$.data.couponDiscounts.realized").value(30))
                .andExpect(jsonPath("$.data.couponDiscounts.canceled").value(10))
                .andExpect(jsonPath("$.data.couponDiscounts.refunded").value(5))
                .andExpect(jsonPath("$.data.lowStockCount").value(1))
                .andExpect(jsonPath("$.data.soldOutTransitionCount").value(1))
                .andExpect(jsonPath("$.data.leadingFailureReasons[0].reason").value("EXHAUSTED"))
                .andExpect(jsonPath("$.data.leadingFailureReasons[0].count").value(2));
    }

    @Test
    void MANAGER는_자기_상점_운영_요약과_드릴다운을_조회한다() throws Exception {
        Member owner = saveMember("dashboard-manager-owner@example.com", "소유자");
        Member manager = saveMember("dashboard-manager@example.com", "매니저");
        Store store = saveBusinessStore(owner, "매니저 지표 상점");
        storeMembershipRepository.save(StoreMembership.createManager(store, manager));
        insertStoreMetric(activeGenerationId, store.getId(), BUCKET, "PAYMENT_FAILED",
                1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        insertCampaignMetric(activeGenerationId, store.getId(), "COUPON", 41L,
                "STORE", store.getId(), "EXHAUSTED", 1, 2, 1, 1, 3, 0, 700);
        insertInventory(activeGenerationId, store.getId(), 201L, true, 3);
        insertAudit(activeGenerationId, store.getId(), 41L, "COUPON", "PAUSE",
                Instant.parse("2026-07-11T02:00:00Z"));

        for (String route : new String[]{
                "dashboard", "campaigns", "coupon-outcomes", "inventory-pressure",
                "purchase-outcomes", "campaign-audits"
        }) {
            mockMvc.perform(period(get("/api/stores/{storeId}/operations/{route}", store.getId(), route))
                            .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void 외부_회원은_상점_운영_지표를_조회할_수_없다() throws Exception {
        Member owner = saveMember("dashboard-denied-owner@example.com", "소유자");
        Member outsider = saveMember("dashboard-denied@example.com", "외부인");
        Store store = saveBusinessStore(owner, "권한 지표 상점");

        mockMvc.perform(period(get("/api/stores/{storeId}/operations/dashboard", store.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void 개인상점은_주문지표를_보고_캠페인은_빈상태로_받는다() throws Exception {
        Member owner = saveMember("dashboard-personal@example.com", "개인 판매자");
        Store store = savePersonalStore(owner, "개인 지표 상점");
        insertStoreMetric(activeGenerationId, store.getId(), BUCKET, "NONE",
                2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        insertCampaignMetric(activeGenerationId, store.getId(), "COUPON", 301L,
                "PLATFORM", 0L, "NONE", 0, 0, 1, 0, 1, 0, 500);

        mockMvc.perform(period(get("/api/stores/{storeId}/operations/dashboard", store.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderSuccessCount").value(2));

        mockMvc.perform(period(get("/api/stores/{storeId}/operations/campaigns", store.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 플랫폼쿠폰이_상점주문에_적용되면_해당상점_할인액에_포함한다() throws Exception {
        Member owner = saveMember("dashboard-platform-coupon@example.com", "소유자");
        Store store = saveBusinessStore(owner, "플랫폼 쿠폰 상점");
        insertStoreMetric(activeGenerationId, store.getId(), BUCKET, "NONE",
                1, 0, 0, 0, 0, 0, 700, 0, 0, 0, 0);
        insertCampaignMetric(activeGenerationId, store.getId(), "COUPON", 51L,
                "PLATFORM", 0L, "NONE", 0, 0, 1, 0, 1, 0, 700);

        mockMvc.perform(period(get("/api/stores/{storeId}/operations/dashboard", store.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponDiscounts.applied").value(700))
                .andExpect(jsonPath("$.data.redemptionSuccessCount").value(1));
    }

    @Test
    void 조회기간은_KST_반개구간으로_변환한다() {
        DashboardPeriod period = new DashboardPeriodResolver().resolve(
                null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                Instant.parse("2026-07-17T00:00:00Z"));

        assertThat(period.fromInclusive()).isEqualTo(Instant.parse("2026-06-30T15:00:00Z"));
        assertThat(period.toExclusive()).isEqualTo(Instant.parse("2026-07-02T15:00:00Z"));
        assertThat(period.timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 사용자지정_91일은_거부한다() throws Exception {
        Member owner = saveMember("dashboard-period@example.com", "소유자");
        Store store = saveBusinessStore(owner, "기간 지표 상점");

        mockMvc.perform(get("/api/stores/{storeId}/operations/dashboard", store.getId())
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-04-01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 드릴다운은_상점범위를_SQL에서_제한하고_결정적으로_페이지한다() throws Exception {
        Member owner = saveMember("dashboard-page-owner@example.com", "소유자");
        Member foreignOwner = saveMember("dashboard-page-foreign@example.com", "다른 소유자");
        Store store = saveBusinessStore(owner, "페이지 지표 상점");
        Store foreignStore = saveBusinessStore(foreignOwner, "다른 지표 상점");
        long firstId = insertAudit(activeGenerationId, store.getId(), 61L, "COUPON", "CREATE",
                Instant.parse("2026-07-11T03:00:00Z"));
        long secondId = insertAudit(activeGenerationId, store.getId(), 62L, "COUPON", "PAUSE",
                Instant.parse("2026-07-11T03:00:00Z"));
        long foreignId = insertAudit(activeGenerationId, foreignStore.getId(), 99L, "COUPON", "END",
                Instant.parse("2026-07-11T04:00:00Z"));

        String firstPage = mockMvc.perform(period(get("/api/stores/{storeId}/operations/campaign-audits", store.getId()))
                        .queryParam("page", "0").queryParam("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();
        String secondPage = mockMvc.perform(period(get("/api/stores/{storeId}/operations/campaign-audits", store.getId()))
                        .queryParam("page", "1").queryParam("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long page0Id = objectMapper.readTree(firstPage).path("data").path("content").get(0).path("id").asLong();
        long page1Id = objectMapper.readTree(secondPage).path("data").path("content").get(0).path("id").asLong();
        assertThat(page0Id).isEqualTo(secondId);
        assertThat(page1Id).isEqualTo(firstId);
        assertThat(page0Id).isNotEqualTo(page1Id).isNotEqualTo(foreignId);
    }

    @Test
    void 요약과_드릴다운은_쿼리수를_제한한다() throws Exception {
        Member owner = saveMember("dashboard-query-count@example.com", "소유자");
        Store store = saveBusinessStore(owner, "쿼리 경계 상점");

        clearInvocations(namedParameterJdbcTemplate);
        mockMvc.perform(period(get("/api/stores/{storeId}/operations/dashboard", store.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        assertThat(executedProjectionSqlCount()).isLessThanOrEqualTo(4);

        clearInvocations(namedParameterJdbcTemplate);
        mockMvc.perform(period(get("/api/stores/{storeId}/operations/campaign-audits", store.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        assertThat(executedProjectionSqlCount()).isLessThanOrEqualTo(3);
    }

    private long executedProjectionSqlCount() {
        return mockingDetails(namedParameterJdbcTemplate).getInvocations().stream()
                .map(invocation -> invocation.getArguments())
                .filter(arguments -> arguments.length > 0 && arguments[0] instanceof String)
                .map(arguments -> ((String) arguments[0]).strip())
                .filter(sql -> Arrays.stream(new String[]{
                                "projection_generations", "store_metric_hourly", "campaign_metric_hourly",
                                "inventory_pressure_projection", "campaign_audit_projection"
                        }).anyMatch(sql::contains))
                .distinct()
                .count();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder period(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.queryParam("from", FROM).queryParam("to", TO);
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
    }

    private Store saveBusinessStore(Member owner, String publicName) {
        Store store = Store.applyBusiness(owner, publicName, "소개", "법인", "123-45-67890");
        store.approve();
        store = storeRepository.save(store);
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        return store;
    }

    private Store savePersonalStore(Member owner, String publicName) {
        Store store = storeRepository.save(Store.createPersonal(owner, publicName, "소개"));
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        return store;
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
    }

    private long insertGeneration(String status, Instant activatedAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO projection_generations (
                    status, cutoff_at, tracking_started_at, bootstrap_high_water_id,
                    activated_at, retired_at
                ) VALUES (?, '2026-07-13T00:00:00Z', '2026-05-01T00:00:00Z', 0, ?, ?)
                RETURNING id
                """, Long.class, status, timestamp(activatedAt),
                "RETIRED".equals(status) ? timestamp(activatedAt) : null);
    }

    private void insertStoreMetric(
            long generationId, long storeId, Instant bucket, String reason,
            long orders, long failures,
            long promotionApplied, long promotionRealized, long promotionCanceled, long promotionRefunded,
            long couponApplied, long couponRealized, long couponCanceled, long couponRefunded,
            long soldOut
    ) {
        jdbcTemplate.update("""
                INSERT INTO store_metric_hourly (
                    generation_id, bucket_start, store_id, outcome_reason,
                    order_success_count, purchase_failure_count,
                    promotion_applied_amount, promotion_realized_amount,
                    promotion_canceled_amount, promotion_refunded_amount,
                    coupon_applied_amount, coupon_realized_amount,
                    coupon_canceled_amount, coupon_refunded_amount,
                    sold_out_transition_count, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, generationId, timestamp(bucket), storeId, reason, orders, failures,
                promotionApplied, promotionRealized, promotionCanceled, promotionRefunded,
                couponApplied, couponRealized, couponCanceled, couponRefunded, soldOut, timestamp(bucket));
    }

    private void insertCampaignMetric(
            long generationId, long commerceStoreId, String kind, long campaignId,
            String ownerType, long ownerStoreId, String reason,
            long claims, long claimFailures, long redemptions, long redemptionFailures,
            long orders, long purchaseFailures, long couponApplied
    ) {
        jdbcTemplate.update("""
                INSERT INTO campaign_metric_hourly (
                    generation_id, bucket_start, commerce_store_id, campaign_kind,
                    campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason,
                    claim_success_count, claim_failure_count,
                    redemption_success_count, redemption_failure_count,
                    order_success_count, purchase_failure_count, coupon_applied_amount, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, generationId, timestamp(BUCKET), commerceStoreId, kind, campaignId, ownerType, ownerStoreId, reason,
                claims, claimFailures, redemptions, redemptionFailures, orders, purchaseFailures,
                couponApplied, timestamp(BUCKET));
    }

    private void insertInventory(long generationId, long storeId, long productId, boolean lowStock, long failures) {
        jdbcTemplate.update("""
                INSERT INTO inventory_pressure_projection (
                    generation_id, product_id, store_id, sales_policy, available_quantity,
                    low_stock, recent_reservation_failure_count, last_reservation_failure_at,
                    aggregate_version, updated_at
                ) VALUES (?, ?, ?, 'STOCK_MANAGED', 3, ?, ?, ?, 1, ?)
                """, generationId, productId, storeId, lowStock, failures, timestamp(BUCKET), timestamp(BUCKET));
        jdbcTemplate.update("""
                INSERT INTO inventory_failure_hourly (
                    generation_id, bucket_start, product_id, store_id, failure_count
                ) VALUES (?, ?, ?, ?, ?)
                """, generationId, timestamp(BUCKET), productId, storeId, failures);
    }

    private long insertAudit(
            long generationId, long storeId, long campaignId, String kind, String command, Instant occurredAt
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO campaign_audit_projection (
                    generation_id, event_id, campaign_kind, campaign_id,
                    owner_type, owner_store_id, actor_member_id, command,
                    occurred_at, aggregate_version, before_summary, after_summary
                ) VALUES (?, ?, ?, ?, 'STORE', ?, 1, ?, ?, 1, NULL, '{}'::jsonb)
                RETURNING id
                """, Long.class, generationId, UUID.randomUUID(), kind, campaignId, storeId, command,
                timestamp(occurredAt));
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
