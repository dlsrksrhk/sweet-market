package com.sweet.market.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.product.api.ProductCreateRequest;
import com.sweet.market.support.IntegrationTestSupport;

class ProductSellerApiTest extends IntegrationTestSupport {

    @Test
    void 판매자는_내_판매_상품을_조회할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-products@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-products@example.com", "password123", "buyer");

        createProduct(sellerToken, "Seller Product", 10_000L);
        createProduct(buyerToken, "Buyer Product", 20_000L);

        mockMvc.perform(get("/api/products/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Seller Product"));
    }

    private void createProduct(String token, String title, long price) throws Exception {
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProductCreateRequest(
                                title,
                                title + " description",
                                price,
                                List.of("https://example.com/" + title.replace(" ", "-").toLowerCase() + ".jpg")
                        ))))
                .andExpect(status().isCreated());
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
}
