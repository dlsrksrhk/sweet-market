package com.sweet.market.operations.campaign;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.operations.projection.OperationalProjectionCoordinator;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class CampaignAuditProjectionTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private OperationalProjectionCoordinator coordinator;

    @BeforeEach
    void 감사_projection_테이블을_준비한다() {
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS projection_event_receipts (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    projection_name VARCHAR(80) NOT NULL,
                    event_id UUID NOT NULL,
                    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_campaign_audit_test_receipt UNIQUE (generation_id, projection_name, event_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS campaign_audit_projection (
                    id BIGSERIAL PRIMARY KEY,
                    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
                    event_id UUID NOT NULL,
                    campaign_kind VARCHAR(20) NOT NULL,
                    campaign_id BIGINT NOT NULL,
                    owner_type VARCHAR(20) NOT NULL,
                    owner_store_id BIGINT NOT NULL DEFAULT 0,
                    actor_member_id BIGINT NOT NULL,
                    command VARCHAR(20) NOT NULL,
                    occurred_at TIMESTAMPTZ NOT NULL,
                    aggregate_version BIGINT,
                    before_summary JSONB,
                    after_summary JSONB NOT NULL,
                    CONSTRAINT uq_campaign_audit_test_generation_event UNIQUE (generation_id, event_id)
                )
                """);
        jdbcTemplate.execute("""
                TRUNCATE TABLE campaign_audit_projection, projection_event_receipts,
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
    void 상점_OWNER의_프로모션_생성과_수정과_수명주기를_감사한다() throws Exception {
        String email = "campaign-audit-owner@example.com";
        String token = signupAndLogin(email);
        Long memberId = memberId(email);
        Long storeId = createBusinessStore(email, "111-22-33333");

        long campaignId = createPromotion(token, storeId, "최초 프로모션")
                .path("data").path("id").asLong();
        updatePromotion(token, storeId, campaignId, "수정 프로모션");
        command(token, "/api/stores/%d/promotions/%d/schedule".formatted(storeId, campaignId));
        command(token, "/api/stores/%d/promotions/%d/pause".formatted(storeId, campaignId));
        command(token, "/api/stores/%d/promotions/%d/resume".formatted(storeId, campaignId));
        command(token, "/api/stores/%d/promotions/%d/end".formatted(storeId, campaignId));

        assertThat(coordinator.projectNextBatch(Instant.now().plusSeconds(1), 100)).isEqualTo(6);
        Instant retryAt = Instant.now().plusSeconds(2);
        jdbcTemplate.update("""
                UPDATE operational_event_outbox
                SET delivery_state = 'RETRY', next_attempt_at = ?
                """, Timestamp.from(retryAt));
        assertThat(coordinator.projectNextBatch(retryAt, 100)).isEqualTo(6);
        assertThat(count("campaign_audit_projection")).isEqualTo(6);
        List<Map<String, Object>> audits = audits();
        assertThat(audits).extracting(row -> row.get("command"))
                .containsExactly("CREATED", "UPDATED", "SCHEDULED", "PAUSED", "RESUMED", "ENDED");
        assertThat(audits).extracting(row -> row.get("aggregate_version"))
                .containsExactly(0L, 1L, 2L, 3L, 4L, 5L);
        assertThat(audits).allSatisfy(row -> {
            assertThat(row.get("owner_type")).isEqualTo("STORE");
            assertThat(row.get("owner_store_id")).isEqualTo(storeId);
            assertThat(row.get("actor_member_id")).isEqualTo(memberId);
        });
        assertStatuses(audits.get(0), null, "DRAFT");
        assertStatuses(audits.get(1), "DRAFT", "DRAFT");
        assertStatuses(audits.get(2), "DRAFT", "SCHEDULED");
        assertStatuses(audits.get(3), "SCHEDULED", "PAUSED");
        assertStatuses(audits.get(4), "PAUSED", "SCHEDULED");
        assertStatuses(audits.get(5), "SCHEDULED", "ENDED");
    }

    @Test
    void 플랫폼_쿠폰_명령은_ADMIN_행위자를_감사한다() throws Exception {
        String email = "campaign-audit-admin@example.com";
        signupAndLogin(email);
        jdbcTemplate.update("UPDATE members SET role = 'ADMIN' WHERE email = ?", email);
        String token = login(email);
        Long memberId = memberId(email);

        long campaignId = createPlatformCoupon(token, "최초 플랫폼 쿠폰")
                .path("data").path("id").asLong();
        updatePlatformCoupon(token, campaignId, "수정 플랫폼 쿠폰");
        command(token, "/api/admin/coupon-campaigns/%d/schedule".formatted(campaignId));
        command(token, "/api/admin/coupon-campaigns/%d/pause".formatted(campaignId));
        command(token, "/api/admin/coupon-campaigns/%d/resume".formatted(campaignId));
        command(token, "/api/admin/coupon-campaigns/%d/end".formatted(campaignId));

        assertThat(coordinator.projectNextBatch(Instant.now().plusSeconds(1), 100)).isEqualTo(6);
        List<Map<String, Object>> audits = audits();
        assertThat(audits).extracting(row -> row.get("command"))
                .containsExactly("CREATED", "UPDATED", "SCHEDULED", "PAUSED", "RESUMED", "ENDED");
        assertThat(audits).allSatisfy(row -> {
            assertThat(row.get("owner_type")).isEqualTo("PLATFORM");
            assertThat(row.get("owner_store_id")).isEqualTo(0L);
            assertThat(row.get("actor_member_id")).isEqualTo(memberId);
        });
    }

    @Test
    void 상점_캠페인을_조회한_ADMIN은_변경_감사를_생성하지_않는다() throws Exception {
        String email = "campaign-audit-read-admin@example.com";
        String ownerToken = signupAndLogin(email);
        Long storeId = createBusinessStore(email, "222-33-44444");
        long promotionId = createPromotion(ownerToken, storeId, "조회 프로모션")
                .path("data").path("id").asLong();
        long couponId = createStoreCoupon(ownerToken, storeId, "조회 쿠폰")
                .path("data").path("id").asLong();
        jdbcTemplate.execute("TRUNCATE TABLE campaign_audit_projection, projection_event_receipts, operational_event_outbox RESTART IDENTITY CASCADE");
        jdbcTemplate.update("UPDATE members SET role = 'ADMIN' WHERE email = ?", email);
        String adminToken = login(email);

        mockMvc.perform(get("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/stores/{storeId}/promotions/{campaignId}", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/stores/{storeId}/coupon-campaigns", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/stores/{storeId}/coupon-campaigns/{campaignId}", storeId, couponId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        assertThat(count("operational_event_outbox")).isZero();
        assertThat(count("campaign_audit_projection")).isZero();
    }

    @Test
    void 감사_payload에_사업자등록번호와_회원_개인정보를_넣지_않는다() throws Exception {
        String email = "private-campaign-owner@example.com";
        String passwordFixture = "private-password-fixture";
        String businessRegistrationId = "987-65-43210";
        String token = signupAndLogin(email, passwordFixture);
        Long storeId = createBusinessStore(email, businessRegistrationId);

        createPromotion(token, storeId, "민감정보 제외 프로모션");

        String payload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM operational_event_outbox", String.class);
        assertThat(payload)
                .doesNotContain("businessRegistrationId", "business_registration_id", businessRegistrationId)
                .doesNotContain("email", email)
                .doesNotContain("password", passwordFixture)
                .doesNotContain("nickname", "판매자");
    }

    private List<Map<String, Object>> audits() {
        return jdbcTemplate.queryForList("""
                SELECT campaign_kind, campaign_id, owner_type, owner_store_id,
                       actor_member_id, command, aggregate_version,
                       before_summary::text AS before_summary,
                       after_summary::text AS after_summary
                FROM campaign_audit_projection
                ORDER BY id
                """);
    }

    private void assertStatuses(Map<String, Object> audit, String before, String after) throws Exception {
        String beforeJson = (String) audit.get("before_summary");
        if (before == null) {
            assertThat(beforeJson).isNull();
        } else {
            assertThat(objectMapper.readTree(beforeJson).path("lifecycleStatus").asText()).isEqualTo(before);
        }
        assertThat(objectMapper.readTree((String) audit.get("after_summary"))
                .path("lifecycleStatus").asText()).isEqualTo(after);
    }

    private JsonNode createPromotion(String token, Long storeId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionJson(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void updatePromotion(String token, Long storeId, long campaignId, String title) throws Exception {
        mockMvc.perform(patch("/api/stores/{storeId}/promotions/{campaignId}", storeId, campaignId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionJson(title)))
                .andExpect(status().isOk());
    }

    private JsonNode createStoreCoupon(String token, Long storeId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/stores/{storeId}/coupon-campaigns", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createPlatformCoupon(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/admin/coupon-campaigns")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void updatePlatformCoupon(String token, long campaignId, String title) throws Exception {
        mockMvc.perform(patch("/api/admin/coupon-campaigns/{campaignId}", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(title)))
                .andExpect(status().isOk());
    }

    private void command(String token, String path) throws Exception {
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private String promotionJson(String title) {
        LocalDateTime startsAt = futureStart();
        return """
                {
                  "scope": "STORE_WIDE",
                  "discountType": "FIXED_AMOUNT",
                  "discountValue": 1000,
                  "priority": 10,
                  "title": "%s",
                  "label": "기간 한정",
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "productIds": []
                }
                """.formatted(title, startsAt, startsAt.plusDays(1));
    }

    private String couponJson(String title) {
        LocalDateTime startsAt = futureStart();
        return """
                {
                  "scope": "ALL_PRODUCTS",
                  "discountType": "FIXED_AMOUNT",
                  "discountValue": 1000,
                  "minimumPurchaseAmount": 0,
                  "stackable": true,
                  "title": "%s",
                  "label": "기간 한정",
                  "issueStartsAt": "%s",
                  "issueEndsAt": "%s",
                  "validityType": "DAYS_FROM_ISSUANCE",
                  "validityDays": 7,
                  "issueLimit": 100,
                  "productIds": []
                }
                """.formatted(title, startsAt, startsAt.plusDays(1));
    }

    private LocalDateTime futureStart() {
        return LocalDateTime.now(KST).plusDays(2).withSecond(0).withNano(0);
    }

    private String signupAndLogin(String email) throws Exception {
        return signupAndLogin(email, "password123");
    }

    private String signupAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, password, "판매자"))))
                .andExpect(status().isCreated());
        return login(email, password);
    }

    private String login(String email) throws Exception {
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private Long createBusinessStore(String email, String businessRegistrationId) {
        Long memberId = memberId(email);
        Long storeId = jdbcTemplate.queryForObject("""
                INSERT INTO stores (
                    version, owner_member_id, type, public_name, introduction,
                    legal_business_name, business_registration_id, status, created_at, updated_at
                ) VALUES (
                    0, ?, 'BUSINESS', '감사 대상 상점', '', '감사 대상 법인', ?,
                    'ACTIVE', current_timestamp, current_timestamp
                ) RETURNING id
                """, Long.class, memberId, businessRegistrationId);
        jdbcTemplate.update("""
                INSERT INTO store_memberships (store_id, member_id, role, active, created_at)
                VALUES (?, ?, 'OWNER', true, current_timestamp)
                """, storeId, memberId);
        return storeId;
    }

    private Long memberId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
