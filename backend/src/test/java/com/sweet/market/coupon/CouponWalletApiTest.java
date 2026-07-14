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
    void 발급된_쿠폰은_사용과_만료와_일시중지_상태를_지갑에_반영한다() throws Exception {
        String token = signupAndLogin("coupon-wallet-status@example.com");
        Long usedCampaignId = activeCampaign();
        Long expiredCampaignId = activeCampaign();
        Long unavailableCampaignId = activeCampaign();
        claim(token, usedCampaignId); claim(token, expiredCampaignId); claim(token, unavailableCampaignId);
        jdbcTemplate.update("update member_coupons set status = 'USED' where coupon_campaign_id = ?", usedCampaignId);
        jdbcTemplate.update("update member_coupons set valid_until = current_timestamp - interval '1 second' where coupon_campaign_id = ?", expiredCampaignId);
        pause(unavailableCampaignId);

        mockMvc.perform(get("/api/me/coupons?size=100").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.campaignId == %d)].status".formatted(usedCampaignId)).value("USED"))
                .andExpect(jsonPath("$.data.content[?(@.campaignId == %d)].status".formatted(expiredCampaignId)).value("EXPIRED"))
                .andExpect(jsonPath("$.data.content[?(@.campaignId == %d)].status".formatted(unavailableCampaignId)).value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.content[?(@.campaignId == %d)].unavailabilityReason".formatted(unavailableCampaignId)).value("PAUSED"));
    }

    private void claim(String token, Long campaignId) throws Exception { mockMvc.perform(post("/api/coupon-campaigns/{campaignId}/claim", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk()); }
    private Long activeCampaign() throws Exception { LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0); String adminToken = adminToken(); String body = "{ \"scope\": \"ALL_PRODUCTS\", \"discountType\": \"FIXED_AMOUNT\", \"discountValue\": 1000, \"minimumPurchaseAmount\": 0, \"stackable\": true, \"title\": \"지갑 쿠폰\", \"issueStartsAt\": \"%s\", \"issueEndsAt\": \"%s\", \"validityType\": \"DAYS_FROM_ISSUANCE\", \"validityDays\": 7, \"productIds\": [] }".formatted(now.minusDays(1), now.plusDays(2)); String response = mockMvc.perform(post("/api/admin/coupon-campaigns").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); Long id = objectMapper.readTree(response).path("data").path("id").asLong(); mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/schedule", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andExpect(status().isOk()); return id; }
    private void pause(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/pause", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private String adminToken() throws Exception { if (jdbcTemplate.queryForObject("select count(*) from members where email = ?", Integer.class, "coupon-wallet-admin@example.com") == 0) signupAndLogin("coupon-wallet-admin@example.com"); jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", "coupon-wallet-admin@example.com"); return login("coupon-wallet-admin@example.com"); }
    private String signupAndLogin(String email) throws Exception { mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(new SignupRequest(email, "password123", "회원")))).andExpect(status().isCreated()); return login(email); }
    private String login(String email) throws Exception { return objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest(email, "password123")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText(); }
}
