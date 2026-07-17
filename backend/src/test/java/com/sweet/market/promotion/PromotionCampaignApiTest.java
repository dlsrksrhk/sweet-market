package com.sweet.market.promotion;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "market.operations-projector.enabled=false")
class PromotionCampaignApiTest extends IntegrationTestSupport {

    @Test
    void 사업자_상점_소유자는_선택상품_프로모션을_예약하고_조회와_수정을_할_수_있다() throws Exception {
        String token = signupAndLogin("promotion-selected@example.com");
        Long storeId = createBusinessStore("promotion-selected@example.com", "ACTIVE");
        Long productId = createProduct(storeId, "ON_SALE");

        String created = create(token, storeId, "SELECTED_PRODUCTS", "[" + productId + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("SELECTED_PRODUCTS"))
                .andExpect(jsonPath("$.data.targetCount").value(1))
                .andExpect(jsonPath("$.data.startsAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Long promotionId = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(get("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("status", "SCHEDULED").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].effectiveStatus").value("SCHEDULED"));

        mockMvc.perform(get("/api/stores/{storeId}/promotions/{promotionId}", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targets", hasSize(1)))
                .andExpect(jsonPath("$.data.targets[0].productId").value(productId));

        mockMvc.perform(patch("/api/stores/{storeId}/promotions/{promotionId}", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("SELECTED_PRODUCTS", "[" + productId + "]").replace("선택 상품 할인", "수정된 선택 상품 할인")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 선택 상품 할인"));
    }

    @Test
    void 사업자_상점_소유자는_상점전체_프로모션을_생성한다() throws Exception {
        String token = signupAndLogin("promotion-store-wide@example.com");
        Long storeId = createBusinessStore("promotion-store-wide@example.com", "ACTIVE");

        create(token, storeId, "STORE_WIDE", "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("STORE_WIDE"))
                .andExpect(jsonPath("$.data.targetCount").value(0))
                .andExpect(jsonPath("$.data.targets").doesNotExist());

        mockMvc.perform(get("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void 개인상점_소유자는_프로모션을_생성할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-personal@example.com");
        Long storeId = activePersonalStoreId(token);

        create(token, storeId, "STORE_WIDE", "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORE_INVALID_TYPE"));
    }

    @Test
    void 매니저는_프로모션을_생성할_수_없다() throws Exception {
        signupAndLogin("promotion-manager-owner@example.com");
        Long storeId = createBusinessStore("promotion-manager-owner@example.com", "ACTIVE");
        String managerToken = signupAndLogin("promotion-manager@example.com");
        Long managerId = memberId("promotion-manager@example.com");
        jdbcTemplate.update("insert into store_memberships (store_id, member_id, role, active, created_at) values (?, ?, 'MANAGER', true, current_timestamp)", storeId, managerId);

        create(managerToken, storeId, "STORE_WIDE", "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));
    }

    @Test
    void 외부_회원은_프로모션을_생성할_수_없다() throws Exception {
        signupAndLogin("promotion-outsider-owner@example.com");
        Long storeId = createBusinessStore("promotion-outsider-owner@example.com", "ACTIVE");
        String outsiderToken = signupAndLogin("promotion-outsider@example.com");

        create(outsiderToken, storeId, "STORE_WIDE", "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));
    }

    @Test
    void 비활성_사업자_상점에는_프로모션을_생성할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-inactive@example.com");
        Long storeId = createBusinessStore("promotion-inactive@example.com", "PENDING");

        create(token, storeId, "STORE_WIDE", "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void 다른_상점_상품은_프로모션_대상으로_선택할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-cross-owner@example.com");
        Long storeId = createBusinessStore("promotion-cross-owner@example.com", "ACTIVE");
        signupAndLogin("promotion-cross-other@example.com");
        Long otherStoreId = createBusinessStore("promotion-cross-other@example.com", "ACTIVE");
        Long otherProductId = createProduct(otherStoreId, "ON_SALE");

        create(token, storeId, "SELECTED_PRODUCTS", "[" + otherProductId + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 구매할_수_없는_상품은_프로모션_대상으로_선택할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-hidden-owner@example.com");
        Long storeId = createBusinessStore("promotion-hidden-owner@example.com", "ACTIVE");
        Long hiddenProductId = createProduct(storeId, "HIDDEN");

        create(token, storeId, "SELECTED_PRODUCTS", "[" + hiddenProductId + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 종료시각이_시작시각보다_빠르면_프로모션을_생성할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-period@example.com");
        Long storeId = createBusinessStore("promotion-period@example.com", "ACTIVE");

        mockMvc.perform(post("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("STORE_WIDE", "[]", futureStart().plusDays(1), futureStart())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 조회기간의_시작이_종료보다_늦으면_목록을_조회할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-search-period@example.com");
        Long storeId = createBusinessStore("promotion-search-period@example.com", "ACTIVE");

        mockMvc.perform(get("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("periodFrom", "2026-12-02T09:00:00")
                        .param("periodTo", "2026-12-01T09:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 예약되지_않은_프로모션은_일시정지할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-lifecycle@example.com");
        Long storeId = createBusinessStore("promotion-lifecycle@example.com", "ACTIVE");
        Long promotionId = objectMapper.readTree(create(token, storeId, "STORE_WIDE", "[]")
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/stores/{storeId}/promotions/{promotionId}/pause", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_LIFECYCLE_NOT_ALLOWED"));
    }

    @Test
    void 예약한_프로모션은_일시정지_재개_종료할_수_있다() throws Exception {
        String token = signupAndLogin("promotion-transitions@example.com");
        Long storeId = createBusinessStore("promotion-transitions@example.com", "ACTIVE");
        Long promotionId = objectMapper.readTree(create(token, storeId, "STORE_WIDE", "[]")
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/stores/{storeId}/promotions/{promotionId}/schedule", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("SCHEDULED"));
        mockMvc.perform(post("/api/stores/{storeId}/promotions/{promotionId}/pause", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveStatus").value("PAUSED"));
        mockMvc.perform(post("/api/stores/{storeId}/promotions/{promotionId}/resume", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("SCHEDULED"));
        mockMvc.perform(post("/api/stores/{storeId}/promotions/{promotionId}/end", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveStatus").value("ENDED"));
    }

    @Test
    void 종료된_일시정지_프로모션은_재개할_수_없다() throws Exception {
        String token = signupAndLogin("promotion-expired-resume@example.com");
        Long storeId = createBusinessStore("promotion-expired-resume@example.com", "ACTIVE");
        Long promotionId = objectMapper.readTree(create(token, storeId, "STORE_WIDE", "[]")
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        jdbcTemplate.update("update promotion_campaigns set lifecycle_status = 'PAUSED', end_at = ? where id = ?",
                java.sql.Timestamp.valueOf("2020-01-01 00:00:00"), promotionId);

        mockMvc.perform(post("/api/stores/{storeId}/promotions/{promotionId}/resume", storeId, promotionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_LIFECYCLE_NOT_ALLOWED"));
    }

    @Test
    void 만료된_일시정지_프로모션은_일시정지_목록에만_노출된다() throws Exception {
        String token = signupAndLogin("promotion-paused-filter@example.com");
        Long storeId = createBusinessStore("promotion-paused-filter@example.com", "ACTIVE");
        Long promotionId = objectMapper.readTree(create(token, storeId, "STORE_WIDE", "[]")
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        jdbcTemplate.update("update promotion_campaigns set lifecycle_status = 'PAUSED', end_at = ? where id = ?",
                java.sql.Timestamp.valueOf("2020-01-01 00:00:00"), promotionId);

        mockMvc.perform(get("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).param("status", "PAUSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].effectiveStatus").value("PAUSED"));
        mockMvc.perform(get("/api/stores/{storeId}/promotions", storeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).param("status", "ENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void 존재하지_않는_프로모션은_찾을_수_없다() throws Exception {
        String token = signupAndLogin("promotion-not-found@example.com");
        Long storeId = createBusinessStore("promotion-not-found@example.com", "ACTIVE");

        mockMvc.perform(get("/api/stores/{storeId}/promotions/{promotionId}", storeId, 99999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
    }

    private ResultActions create(String token, Long storeId, String scope, String productIds) throws Exception {
        return mockMvc.perform(post("/api/stores/{storeId}/promotions", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(scope, productIds)));
    }

    private String requestJson(String scope, String productIds) {
        LocalDateTime startsAt = futureStart();
        return requestJson(scope, productIds, startsAt, startsAt.plusDays(1));
    }

    private String requestJson(String scope, String productIds, LocalDateTime startsAt, LocalDateTime endsAt) {
        return """
                {
                  "scope": "%s",
                  "discountType": "FIXED_AMOUNT",
                  "discountValue": 1000,
                  "priority": 10,
                  "title": "선택 상품 할인",
                  "label": "기간 한정",
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "productIds": %s
                }
                """.formatted(scope, startsAt, endsAt, productIds);
    }

    private LocalDateTime futureStart() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(2).withSecond(0).withNano(0);
    }

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, "password123", "판매자"))))
                .andExpect(status().isCreated());
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private Long createBusinessStore(String email, String status) {
        Long memberId = memberId(email);
        Long storeId = jdbcTemplate.queryForObject("""
                insert into stores (version, owner_member_id, type, public_name, introduction, status, created_at, updated_at)
                values (0, ?, 'BUSINESS', '프로모션 사업자 상점', '', ?, current_timestamp, current_timestamp) returning id
                """, Long.class, memberId, status);
        jdbcTemplate.update("insert into store_memberships (store_id, member_id, role, active, created_at) values (?, ?, 'OWNER', true, current_timestamp)", storeId, memberId);
        return storeId;
    }

    private Long createProduct(Long storeId, String status) {
        return jdbcTemplate.queryForObject("""
                insert into products (version, store_id, title, description, price, status, sales_policy, category)
                values (0, ?, '프로모션 상품', '설명', 10000, ?, 'SINGLE_ITEM', 'OTHER') returning id
                """, Long.class, storeId, status);
    }

    private Long memberId(String email) {
        return jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, email);
    }
}
