package com.sweet.market.product;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class ProductApiTest extends IntegrationTestSupport {

    @Test
    void 상품_등록에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "imageUrls": [
                                    "https://example.com/macbook-1.jpg",
                                    "https://example.com/macbook-2.jpg"
                                  ]
                                }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.sellerId").isNumber())
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.description").value("M3 laptop"))
                .andExpect(jsonPath("$.data.price").value(2000000))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].imageUrl").value("https://example.com/macbook-1.jpg"))
                .andExpect(jsonPath("$.data.images[1].imageUrl").value("https://example.com/macbook-2.jpg"));
    }

    @Test
    void 상품_등록은_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "imageUrls": []
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 잘못된_상품_등록_요청은_검증_오류를_반환한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "description": "",
                                  "price": 0,
                                  "imageUrls": ["not-url"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void 소유자는_상품_수정에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "iPhone 15 Pro",
                                  "description": "Natural titanium",
                                  "price": 1200000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.title").value("iPhone 15 Pro"))
                .andExpect(jsonPath("$.data.description").value("Natural titanium"))
                .andExpect(jsonPath("$.data.price").value(1200000));
    }

    @Test
    void 소유자가_아니면_상품_수정에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(patch("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "iPhone 15 Pro",
                                  "description": "Natural titanium",
                                  "price": 1200000
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
    }

    @Test
    void 소유자는_상품_숨김에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    void 소유자가_아니면_상품_숨김에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
    }

    @Test
    void JWT_없이_판매중_상품_목록을_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        createProduct(sellerToken);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value("https://example.com/macbook-1.jpg"));
    }

    @Test
    void JWT_없이_판매중_상품_상세를_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.images", hasSize(1)));
    }

    @Test
    void 숨김_상품은_공개_목록과_상세_조회에서_제외된다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(delete("/api/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 소유자는_상품_이미지_추가에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);

        mockMvc.perform(post("/api/products/{productId}/images", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageUrl": "https://example.com/macbook-2.jpg"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[1].imageUrl").value("https://example.com/macbook-2.jpg"));
    }

    @Test
    void 소유자가_아니면_상품_이미지_추가에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);

        mockMvc.perform(post("/api/products/{productId}/images", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageUrl": "https://example.com/macbook-2.jpg"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
    }

    @Test
    void 소유자는_상품_이미지_삭제에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);
        Long imageId = getFirstImageId(productId);

        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(0)));
    }

    @Test
    void 상품에_속하지_않은_이미지를_삭제하면_실패한다() throws Exception {
        String accessToken = signupAndLogin("seller@example.com", "password123", "seller");
        Long productId = createProduct(accessToken);

        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_NOT_FOUND"));
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        SignupRequest signupRequest = new SignupRequest(email, password, nickname);
        LoginRequest loginRequest = new LoginRequest(email, password);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private Long createProduct(String accessToken) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "imageUrls": [
                                    "https://example.com/macbook-1.jpg"
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private Long getFirstImageId(Long productId) throws Exception {
        String response = mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("images").get(0).path("id").asLong();
    }
}
