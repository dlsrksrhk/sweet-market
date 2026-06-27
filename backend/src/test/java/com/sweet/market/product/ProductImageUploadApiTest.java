package com.sweet.market.product;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class ProductImageUploadApiTest extends IntegrationTestSupport {

    @Test
    void 상품_이미지_임시_업로드에_성공한다() throws Exception {
        String accessToken = signupAndLogin("seller-upload@example.com", "password123", "seller");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.previewUrl", endsWith(".jpg")))
                .andExpect(jsonPath("$.data.originalFileName").value("product.jpg"))
                .andExpect(jsonPath("$.data.contentType").value(MediaType.IMAGE_JPEG_VALUE))
                .andExpect(jsonPath("$.data.size").value(4))
                .andExpect(jsonPath("$.data.expiresAt", not(blankOrNullString())));
    }

    @Test
    void 상품_이미지_임시_업로드는_JWT가_필요하다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        mockMvc.perform(multipart("/api/product-image-uploads").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 지원하지_않는_상품_이미지_형식은_업로드할_수_없다() throws Exception {
        String accessToken = signupAndLogin("seller-upload-invalid@example.com", "password123", "seller");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_INVALID_FILE"));
    }

    @Test
    void 업로드된_상품_이미지_미리보기는_인증_없이_조회할_수_있다() throws Exception {
        String accessToken = signupAndLogin("seller-upload-preview@example.com", "password123", "seller");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
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
        String previewUrl = root.path("data").path("previewUrl").asText();

        mockMvc.perform(get(previewUrl))
                .andExpect(status().isOk());
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
