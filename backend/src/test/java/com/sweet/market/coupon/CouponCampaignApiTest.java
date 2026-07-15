package com.sweet.market.coupon;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class CouponCampaignApiTest extends IntegrationTestSupport {

    @Test
    void 발급한도와_발급현황을_캠페인_생성_응답으로_확인한다() throws Exception {
        String token = signupAndLogin("coupon-limit@example.com");
        Long storeId = createBusinessStore("coupon-limit@example.com", "ACTIVE");

        createStoreCampaign(token, storeId, "ALL_PRODUCTS", "[]", 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.issueLimit").value(2))
                .andExpect(jsonPath("$.data.issuedCount").value(0))
                .andExpect(jsonPath("$.data.remainingIssueCount").value(2));
    }

    @Test
    void 발급한도는_0으로_생성할_수_없다() throws Exception {
        String token = signupAndLogin("coupon-limit-invalid@example.com");
        Long storeId = createBusinessStore("coupon-limit-invalid@example.com", "ACTIVE");

        createStoreCampaign(token, storeId, "ALL_PRODUCTS", "[]", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 사업자_상점_소유자는_선택상품과_전체상품_쿠폰을_생성한다() throws Exception {
        String token = signupAndLogin("coupon-owner@example.com");
        Long storeId = createBusinessStore("coupon-owner@example.com", "ACTIVE");
        Long productId = createProduct(storeId, "ON_SALE");

        createStoreCampaign(token, storeId, "SELECTED_PRODUCTS", "[" + productId + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerType").value("STORE"))
                .andExpect(jsonPath("$.data.targetCount").value(1))
                .andExpect(jsonPath("$.data.targets", hasSize(1)));
        createStoreCampaign(token, storeId, "ALL_PRODUCTS", "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetCount").value(0))
                .andExpect(jsonPath("$.data.issueLimit").value(nullValue()))
                .andExpect(jsonPath("$.data.issuedCount").value(0))
                .andExpect(jsonPath("$.data.remainingIssueCount").value(nullValue()));
    }

    @Test
    void 관리자는_여러_상점_상품을_대상으로_플랫폼_쿠폰을_예약한다() throws Exception {
        signupAndLogin("coupon-admin@example.com");
        jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", "coupon-admin@example.com");
        String adminToken = login("coupon-admin@example.com");
        String firstOwner = "coupon-platform-first@example.com";
        String secondOwner = "coupon-platform-second@example.com";
        signupAndLogin(firstOwner);
        signupAndLogin(secondOwner);
        Long firstProductId = createProduct(createBusinessStore(firstOwner, "ACTIVE"), "ON_SALE");
        Long secondProductId = createProduct(createBusinessStore(secondOwner, "ACTIVE"), "ON_SALE");

        createPlatformCampaign(adminToken, "SELECTED_PRODUCTS", "[" + firstProductId + "," + secondProductId + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerType").value("PLATFORM"))
                .andExpect(jsonPath("$.data.targetCount").value(2));
    }

    @Test
    void 매니저와_외부회원과_개인상점_소유자와_비활성상점_소유자는_상점_쿠폰을_생성할_수_없다() throws Exception {
        signupAndLogin("coupon-denial-owner@example.com");
        Long storeId = createBusinessStore("coupon-denial-owner@example.com", "ACTIVE");
        String managerToken = signupAndLogin("coupon-manager@example.com");
        jdbcTemplate.update("insert into store_memberships (store_id, member_id, role, active, created_at) values (?, ?, 'MANAGER', true, current_timestamp)", storeId, memberId("coupon-manager@example.com"));
        createStoreCampaign(managerToken, storeId, "ALL_PRODUCTS", "[]")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));
        String outsiderToken = signupAndLogin("coupon-outsider@example.com");
        createStoreCampaign(outsiderToken, storeId, "ALL_PRODUCTS", "[]")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));
        String personalToken = signupAndLogin("coupon-personal@example.com");
        createStoreCampaign(personalToken, activePersonalStoreId(personalToken), "ALL_PRODUCTS", "[]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STORE_INVALID_TYPE"));
        String inactiveToken = signupAndLogin("coupon-inactive@example.com");
        createStoreCampaign(inactiveToken, createBusinessStore("coupon-inactive@example.com", "PENDING"), "ALL_PRODUCTS", "[]")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void 유효하지_않은_대상과_유효기간_정책은_쿠폰_생성을_거부한다() throws Exception {
        String token = signupAndLogin("coupon-invalid@example.com");
        Long storeId = createBusinessStore("coupon-invalid@example.com", "ACTIVE");
        Long hiddenProductId = createProduct(storeId, "HIDDEN");
        createStoreCampaign(token, storeId, "SELECTED_PRODUCTS", "[999999]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        createStoreCampaign(token, storeId, "SELECTED_PRODUCTS", "[" + hiddenProductId + "]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/stores/{storeId}/coupon-campaigns", storeId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson("ALL_PRODUCTS", "[]").replace("\"validityType\": \"DAYS_FROM_ISSUANCE\",\n  \"validityDays\": 7", "\"validityType\": \"COMMON_EXPIRY\",\n  \"validityDays\": 7")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 일반_회원은_플랫폼_쿠폰_명령을_수행할_수_없다() throws Exception {
        String token = signupAndLogin("coupon-not-admin@example.com");
        createPlatformCampaign(token, "ALL_PRODUCTS", "[]")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 상점_쿠폰은_목록과_상세와_수명주기를_관리한다() throws Exception {
        String token = signupAndLogin("coupon-lifecycle@example.com");
        Long storeId = createBusinessStore("coupon-lifecycle@example.com", "ACTIVE");
        Long campaignId = objectMapper.readTree(createStoreCampaign(token, storeId, "ALL_PRODUCTS", "[]").andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(get("/api/stores/{storeId}/coupon-campaigns", storeId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(1)));
        mockMvc.perform(post("/api/stores/{storeId}/coupon-campaigns/{campaignId}/schedule", storeId, campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lifecycleStatus").value("SCHEDULED"));
        mockMvc.perform(post("/api/stores/{storeId}/coupon-campaigns/{campaignId}/pause", storeId, campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.effectiveStatus").value("PAUSED"));
    }

    private ResultActions createStoreCampaign(String token, Long storeId, String scope, String productIds) throws Exception {
        return mockMvc.perform(post("/api/stores/{storeId}/coupon-campaigns", storeId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(requestJson(scope, productIds)));
    }
    private ResultActions createStoreCampaign(String token, Long storeId, String scope, String productIds, Integer issueLimit) throws Exception {
        return mockMvc.perform(post("/api/stores/{storeId}/coupon-campaigns", storeId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(requestJson(scope, productIds, issueLimit)));
    }
    private ResultActions createPlatformCampaign(String token, String scope, String productIds) throws Exception {
        return mockMvc.perform(post("/api/admin/coupon-campaigns").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(requestJson(scope, productIds)));
    }
    private String requestJson(String scope, String productIds) {
        return requestJson(scope, productIds, null);
    }
    private String requestJson(String scope, String productIds, Integer issueLimit) {
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(2).withSecond(0).withNano(0);
        return """
                { "scope": "%s", "discountType": "FIXED_AMOUNT", "discountValue": 1000,
                  "minimumPurchaseAmount": 0, "stackable": true, "title": "쿠폰 할인", "label": "기간 한정",
                  "issueStartsAt": "%s", "issueEndsAt": "%s", "validityType": "DAYS_FROM_ISSUANCE",
                  "validityDays": 7, "issueLimit": %s, "productIds": %s }
                """.formatted(scope, start, start.plusDays(1), issueLimit == null ? "null" : issueLimit, productIds);
    }
    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(new SignupRequest(email, "password123", "판매자")))).andExpect(status().isCreated());
        return login(email);
    }
    private String login(String email) throws Exception { return objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest(email, "password123")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText(); }
    private Long createBusinessStore(String email, String status) { Long id = jdbcTemplate.queryForObject("insert into stores (version, owner_member_id, type, public_name, introduction, status, created_at, updated_at) values (0, ?, 'BUSINESS', '쿠폰 사업자 상점', '', ?, current_timestamp, current_timestamp) returning id", Long.class, memberId(email), status); jdbcTemplate.update("insert into store_memberships (store_id, member_id, role, active, created_at) values (?, ?, 'OWNER', true, current_timestamp)", id, memberId(email)); return id; }
    private Long createProduct(Long storeId, String status) { return jdbcTemplate.queryForObject("insert into products (version, store_id, title, description, price, status, sales_policy, category) values (0, ?, '쿠폰 상품', '설명', 10000, ?, 'SINGLE_ITEM', 'OTHER') returning id", Long.class, storeId, status); }
    private Long memberId(String email) { return jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, email); }
}
