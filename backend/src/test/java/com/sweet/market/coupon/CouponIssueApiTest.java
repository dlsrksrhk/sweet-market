package com.sweet.market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
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

class CouponIssueApiTest extends IntegrationTestSupport {

    @Test
    void 같은_캠페인을_두번_발급해도_한장만_생성하고_같은_쿠폰을_반환한다() throws Exception {
        String token = signupAndLogin("coupon-claim@example.com");
        Long campaignId = activeCampaign();

        String firstBody = claim(token, campaignId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long firstCouponId = objectMapper.readTree(firstBody).path("data").path("id").asLong();

        claim(token, campaignId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstCouponId));
        assertThat(countMemberCoupons(campaignId, memberId("coupon-claim@example.com"))).isEqualTo(1);
    }

    @Test
    void 공통만료일과_발급일기준_유효기간으로_쿠폰을_발급한다() throws Exception {
        String token = signupAndLogin("coupon-validity@example.com");
        Long commonExpiryCampaign = activeCampaign("COMMON_EXPIRY", 5);
        Long issuanceDaysCampaign = activeCampaign("DAYS_FROM_ISSUANCE", 3);

        claim(token, commonExpiryCampaign).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validUntil").exists());
        claim(token, issuanceDaysCampaign).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validUntil").exists());
        assertThat(jdbcTemplate.queryForObject("select valid_until = (select common_expires_at from coupon_campaigns where id = ?) from member_coupons where coupon_campaign_id = ?", Boolean.class, commonExpiryCampaign, commonExpiryCampaign)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select valid_until - issued_at = interval '3 days' from member_coupons where coupon_campaign_id = ?", Boolean.class, issuanceDaysCampaign)).isTrue();
    }

    @Test
    void 미래와_일시중지와_종료된_캠페인은_발급할_수_없다() throws Exception {
        String token = signupAndLogin("coupon-rejection@example.com");
        Long futureCampaign = campaign(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1), LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(2), "DAYS_FROM_ISSUANCE", 7);
        schedule(futureCampaign);
        Long pausedCampaign = activeCampaign();
        pause(pausedCampaign);
        Long endedCampaign = activeCampaign();
        end(endedCampaign);

        claim(token, futureCampaign).andExpect(status().isConflict());
        claim(token, pausedCampaign).andExpect(status().isConflict());
        claim(token, endedCampaign).andExpect(status().isConflict());
    }

    private ResultActions claim(String token, Long campaignId) throws Exception {
        return mockMvc.perform(post("/api/coupon-campaigns/{campaignId}/claim", campaignId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long activeCampaign() throws Exception { return activeCampaign("DAYS_FROM_ISSUANCE", 7); }
    private Long activeCampaign(String validityType, int validityDays) throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);
        Long id = campaign(now.minusDays(1), now.plusDays(2), validityType, validityDays);
        schedule(id);
        return id;
    }
    private Long campaign(LocalDateTime start, LocalDateTime end, String validityType, int validityDays) throws Exception {
        String adminToken = adminToken();
        String validity = "COMMON_EXPIRY".equals(validityType)
                ? "\"commonExpiresAt\": \"%s\",".formatted(end.plusDays(validityDays)) : "\"validityDays\": %d,".formatted(validityDays);
        String body = """
                { "scope": "ALL_PRODUCTS", "discountType": "FIXED_AMOUNT", "discountValue": 1000,
                  "minimumPurchaseAmount": 0, "stackable": true, "title": "발급 쿠폰", "label": "발급",
                  "issueStartsAt": "%s", "issueEndsAt": "%s", "validityType": "%s", %s "productIds": [] }
                """.formatted(start, end, validityType, validity);
        String response = mockMvc.perform(post("/api/admin/coupon-campaigns").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
    private void schedule(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/schedule", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private void pause(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/pause", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private void end(Long campaignId) throws Exception { mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/end", campaignId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())).andExpect(status().isOk()); }
    private String adminToken() throws Exception { if (jdbcTemplate.queryForObject("select count(*) from members where email = ?", Integer.class, "coupon-admin@example.com") == 0) signupAndLogin("coupon-admin@example.com"); jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", "coupon-admin@example.com"); return login("coupon-admin@example.com"); }
    private String signupAndLogin(String email) throws Exception { mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(new SignupRequest(email, "password123", "회원")))).andExpect(status().isCreated()); return login(email); }
    private String login(String email) throws Exception { return objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest(email, "password123")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText(); }
    private Long memberId(String email) { return jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, email); }
    private int countMemberCoupons(Long campaignId, Long memberId) { return jdbcTemplate.queryForObject("select count(*) from member_coupons where coupon_campaign_id = ? and member_id = ?", Integer.class, campaignId, memberId); }
}
