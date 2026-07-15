package com.sweet.market.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class CouponEligibilityApiTest extends IntegrationTestSupport {

    @Test
    void 중지된_캠페인의_유효한_쿠폰도_정확한_할인금액으로_조회한다() throws Exception {
        String buyerToken = signupAndLogin("eligible-buyer@example.com");
        String sellerToken = signupAndLogin("eligible-seller@example.com");
        Long productId = createProduct(sellerToken);
        Long campaignId = activeCampaign("적용 가능 쿠폰");
        claim(buyerToken, campaignId);
        pause(campaignId);

        mockMvc.perform(get("/api/me/coupons/eligible?productId={productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("적용 가능 쿠폰"))
                .andExpect(jsonPath("$.data[0].discountAmount").value(1_000))
                .andExpect(jsonPath("$.data[0].finalPrice").value(9_000))
                .andExpect(jsonPath("$.data[0].validUntil").exists());
    }

    @Test
    void 종료된_캠페인의_유효한_쿠폰도_적용가능_목록에_남는다() throws Exception {
        String buyerToken = signupAndLogin("eligible-ended-buyer@example.com");
        String sellerToken = signupAndLogin("eligible-ended-seller@example.com");
        Long productId = createProduct(sellerToken);
        Long campaignId = activeCampaign("종료 후 사용 가능");
        claim(buyerToken, campaignId);
        end(campaignId);

        mockMvc.perform(get("/api/me/coupons/eligible?productId={productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("종료 후 사용 가능"));
    }

    @Test
    void 사용됨_만료됨_예약중인_쿠폰은_적용가능_목록에서_제외한다() throws Exception {
        String buyerToken = signupAndLogin("eligible-filter-buyer@example.com");
        String sellerToken = signupAndLogin("eligible-filter-seller@example.com");
        Long productId = createProduct(sellerToken);
        Long eligibleCampaign = activeCampaign("사용 가능");
        Long usedCampaign = activeCampaign("사용 완료");
        Long expiredCampaign = activeCampaign("만료");
        Long reservedCampaign = activeCampaign("예약");
        claim(buyerToken, eligibleCampaign);
        claim(buyerToken, usedCampaign);
        claim(buyerToken, expiredCampaign);
        claim(buyerToken, reservedCampaign);
        jdbcTemplate.update("update member_coupons set status = 'USED' where coupon_campaign_id = ?", usedCampaign);
        jdbcTemplate.update("update member_coupons set valid_until = current_timestamp - interval '1 second' where coupon_campaign_id = ?", expiredCampaign);
        Long orderId = createOrder(buyerToken, productId);
        Long reservedCouponId = jdbcTemplate.queryForObject("select id from member_coupons where coupon_campaign_id = ?", Long.class, reservedCampaign);
        jdbcTemplate.update("""
                insert into coupon_reservations (member_coupon_id, order_id, status, reserved_at, expires_at)
                values (?, ?, 'RESERVED', current_timestamp, current_timestamp + interval '30 minutes')
                """, reservedCouponId, orderId);

        mockMvc.perform(get("/api/me/coupons/eligible?productId={productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("사용 가능"));
    }

    private Long activeCampaign(String title) throws Exception {
        Long id = draftCampaign(title);
        mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/schedule", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk());
        return id;
    }

    private Long draftCampaign(String title) throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);
        String body = """
                { "scope": "ALL_PRODUCTS", "discountType": "FIXED_AMOUNT", "discountValue": 1000,
                  "minimumPurchaseAmount": 0, "stackable": true, "title": "%s",
                  "issueStartsAt": "%s", "issueEndsAt": "%s", "validityType": "DAYS_FROM_ISSUANCE",
                  "validityDays": 7, "productIds": [] }
                """.formatted(title, now.minusDays(1), now.plusDays(2));
        String response = mockMvc.perform(post("/api/admin/coupon-campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void claim(String token, Long campaignId) throws Exception {
        mockMvc.perform(post("/api/coupon-campaigns/{campaignId}/claim", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void pause(Long campaignId) throws Exception {
        mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/pause", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    private void end(Long campaignId) throws Exception {
        mockMvc.perform(post("/api/admin/coupon-campaigns/{campaignId}/end", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    private Long createProduct(String token) throws Exception {
        Long storeId = activePersonalStoreId(token);
        Long uploadId = uploadImage(token);
        String response = mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "storeId": %d, "title": "적용 대상 상품", "description": "설명", "price": 10000,
                                  "salesPolicy": "SINGLE_ITEM", "images": [{ "uploadId": %d, "sortOrder": 0, "representative": true }] }
                                """.formatted(storeId, uploadId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long uploadImage(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "coupon.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        String response = mockMvc.perform(multipart("/api/product-image-uploads").file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createOrder(String token, Long productId) throws Exception {
        String response = mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":%d}".formatted(productId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private String adminToken() throws Exception {
        if (jdbcTemplate.queryForObject("select count(*) from members where email = ?", Integer.class, "eligible-admin@example.com") == 0) {
            signupAndLogin("eligible-admin@example.com");
        }
        jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", "eligible-admin@example.com");
        return login("eligible-admin@example.com");
    }

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, "password123", "회원"))))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
