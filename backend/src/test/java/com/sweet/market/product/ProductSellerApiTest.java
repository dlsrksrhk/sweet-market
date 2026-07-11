package com.sweet.market.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManagerFactory;

class ProductSellerApiTest extends IntegrationTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 판매자는_내_판매_상품을_조회할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-products@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-products@example.com", "password123", "buyer");

        createProduct(sellerToken, "Seller Product", 10_000L);
        createProduct(buyerToken, "Buyer Product", 20_000L);

        Statistics statistics = statistics();
        statistics.clear();

        mockMvc.perform(get("/api/products/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Seller Product"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl", startsWith("/uploads/products/public/")));

        assertEquals(0L, statistics.getCollectionFetchCount());
    }

    @Test
    void 내_판매_상품_조회는_대표_이미지를_썸네일로_응답한다() throws Exception {
        String sellerToken = signupAndLogin("seller-representative-products@example.com", "password123", "seller");
        Long storeId = activePersonalStoreId(sellerToken);
        ProductImageUploadFixture firstImage = uploadProductImage(sellerToken, "seller-product-first.jpg");
        ProductImageUploadFixture representativeImage = uploadProductImage(sellerToken, "seller-product-representative.jpg");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "Seller Product",
                                  "description": "Seller Product description",
                                  "price": 10000,
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": false
                                    },
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 1,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(storeId, firstImage.id(), representativeImage.id())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value(representativeImage.publicUrl()));
    }

    @Test
    void 내_판매_상품_조회는_JWT가_필요하다() throws Exception {
        mockMvc.perform(get("/api/products/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private void createProduct(String token, String title, long price) throws Exception {
        Long storeId = activePersonalStoreId(token);
        Long uploadId = uploadImage(token, imageFileName(title));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "%s",
                                  "description": "%s description",
                                  "price": %d,
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(storeId, title, title, price, uploadId)))
                .andExpect(status().isCreated());
    }

    private Long uploadImage(String accessToken, String fileName) throws Exception {
        return uploadProductImage(accessToken, fileName).id();
    }

    private ProductImageUploadFixture uploadProductImage(String accessToken, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        String response = mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.path("data");
        return new ProductImageUploadFixture(
                data.path("id").asLong(),
                data.path("previewUrl").asText().replace("/uploads/products/temp/", "/uploads/products/public/")
        );
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    private String imageFileName(String title) {
        return title.replace(" ", "-").toLowerCase() + ".jpg";
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, password, nickname))))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private record ProductImageUploadFixture(Long id, String publicUrl) {
    }
}
