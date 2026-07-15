package com.sweet.market.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class CouponWalletApiTest extends IntegrationTestSupport {

    @Test
    void 활성_캠페인은_발급여부와_회원별_쿠폰지갑을_페이지로_조회한다() throws Exception {
        String firstToken = signupAndLogin("coupon-wallet-first@example.com");
        String secondToken = signupAndLogin("coupon-wallet-second@example.com");
        Long campaignId = activeCampaign();

        mockMvc.perform(get("/api/coupon-campaigns/available").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(campaignId))
                .andExpect(jsonPath("$.data.content[0].claimed").value(false));
        claim(firstToken, campaignId);
        mockMvc.perform(get("/api/coupon-campaigns/available").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].claimed").value(true));
        mockMvc.perform(get("/api/coupon-campaigns/available").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].claimed").value(false));
        mockMvc.perform(get("/api/me/coupons?page=0&size=1").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].campaignId").value(campaignId));
        mockMvc.perform(get("/api/me/coupons").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void 발급가능_목록과_쿠폰지갑은_출처와_적용상점_정보를_반환하고_출처로_필터링한다() throws Exception {
        String token = signupAndLogin("coupon-wallet-source@example.com");
        Long campaignId = activeCampaign();

        mockMvc.perform(get("/api/coupon-campaigns/available?source=PLATFORM")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(campaignId))
                .andExpect(jsonPath("$.data.content[0].source").value("PLATFORM"))
                .andExpect(jsonPath("$.data.content[0].store").doesNotExist());
        claim(token, campaignId);
        mockMvc.perform(get("/api/me/coupons").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].source").value("PLATFORM"))
                .andExpect(jsonPath("$.data.content[0].store").doesNotExist());
    }

    @Test
    void 쿠폰지갑은_발급시각과_ID_내림차순_페이지에서_모든_유효상태를_반영한다() throws Exception {
        String token = signupAndLogin("coupon-wallet-status@example.com");
        Long issuedCampaignId = activeCampaign();
        Long usedCampaignId = activeCampaign();
        Long expiredCampaignId = activeCampaign();
        Long unavailableCampaignId = activeCampaign();
        claim(token, issuedCampaignId); claim(token, usedCampaignId); claim(token, expiredCampaignId); claim(token, unavailableCampaignId);
        setIssuedAt(issuedCampaignId, "2026-07-14T10:00:00Z");
        setIssuedAt(usedCampaignId, "2026-07-14T10:00:00Z");
        setIssuedAt(expiredCampaignId, "2026-07-14T09:00:00Z");
        setIssuedAt(unavailableCampaignId, "2026-07-14T08:00:00Z");
        jdbcTemplate.update("update member_coupons set status = 'USED' where coupon_campaign_id = ?", usedCampaignId);
        jdbcTemplate.update("update member_coupons set valid_until = current_timestamp - interval '1 second' where coupon_campaign_id = ?", expiredCampaignId);
        pause(unavailableCampaignId);

        mockMvc.perform(get("/api/me/coupons?page=0&size=2").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].campaignId").value(usedCampaignId))
                .andExpect(jsonPath("$.data.content[0].status").value("USED"))
                .andExpect(jsonPath("$.data.content[1].campaignId").value(issuedCampaignId))
                .andExpect(jsonPath("$.data.content[1].status").value("ISSUED"));
        mockMvc.perform(get("/api/me/coupons?page=1&size=2").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].campaignId").value(expiredCampaignId))
                .andExpect(jsonPath("$.data.content[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.content[1].campaignId").value(unavailableCampaignId))
                .andExpect(jsonPath("$.data.content[1].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.content[1].unavailabilityReason").value("PAUSED"));

        mockMvc.perform(get("/api/me/coupons?status=ISSUED&page=0&size=20").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].campaignId").value(issuedCampaignId))
                .andExpect(jsonPath("$.data.content[0].status").value("ISSUED"));
    }

    private void claim(String token, Long campaignId) throws Exception { mockMvc.perform(post("/api/coupon-campaigns/{campaignId}/claim", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk()); }
    private void setIssuedAt(Long campaignId, String issuedAt) { jdbcTemplate.update("update member_coupons set issued_at = cast(? as timestamp with time zone) where coupon_campaign_id = ?", issuedAt, campaignId); }
    private Long activeCampaign() throws Exception { LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0); String adminToken = adminToken(); String body = "{ \"scope\": \"ALL_PRODUCTS\", \"discountType\": \"FIXED_AMOUNT\", \"discountValue\": 1000, \"minimumPurchaseAmount\": 0, \"stackable\": true, \"title\": \"지갑 쿠폰\", \"issueStartsAt\": \"%s\", \"issueEndsAt\": \"%s\", \"validityType\": \"DAYS_FROM_ISSUANCE\", \"validityDays\": 7, \"productIds\": [] }".formatted(now.minusDays(1), now.plusDays(2)); String response = mockMvc.perform(post("/api/admin/coupon-campaigns").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); Long id = objectMapper.readTree(response).path("data").path("id").asLong(); mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/schedule", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andExpect(status().isOk()); return id; }
    private void pause(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/pause", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private String adminToken() throws Exception { if (jdbcTemplate.queryForObject("select count(*) from members where email = ?", Integer.class, "coupon-wallet-admin@example.com") == 0) signupAndLogin("coupon-wallet-admin@example.com"); jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", "coupon-wallet-admin@example.com"); return login("coupon-wallet-admin@example.com"); }
    private String signupAndLogin(String email) throws Exception { mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(new SignupRequest(email, "password123", "회원")))).andExpect(status().isCreated()); return login(email); }
    private String login(String email) throws Exception { return objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest(email, "password123")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText(); }
}
