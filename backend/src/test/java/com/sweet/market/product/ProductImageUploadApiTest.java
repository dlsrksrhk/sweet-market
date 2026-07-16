package com.sweet.market.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.api.ProductImageUploadResponse;
import com.sweet.market.product.application.ProductImageUploadService;
import com.sweet.market.product.storage.ProductImageStorageProperties;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductImageUploadApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductImageStorageProperties storageProperties;

    @Autowired
    private RollbackUploadService rollbackUploadService;

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

    @Test
    void 임시_업로드_후_트랜잭션이_롤백되면_파일을_삭제한다() {
        Member uploader = memberRepository.save(Member.create(
                "seller-upload-rollback@example.com",
                "password123",
                "seller-upload-rollback"
        ));
        MockMultipartFile file = jpegFile("rollback.jpg");
        AtomicReference<String> storedFileName = new AtomicReference<>();

        assertThatThrownBy(() -> rollbackUploadService.uploadAndRollback(
                uploader.getId(),
                file,
                response -> storedFileName.set(storedFileName(response.previewUrl()))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        Path tempFile = storageProperties.tempPath().resolve(storedFileName.get());
        assertThat(Files.exists(tempFile)).isFalse();
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

    private MockMultipartFile jpegFile(String originalFileName) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );
    }

    private String storedFileName(String imageUrl) {
        return imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
    }

    @TestConfiguration
    static class RollbackUploadTestConfiguration {

        @Bean
        RollbackUploadService rollbackUploadService(ProductImageUploadService productImageUploadService) {
            return new RollbackUploadService(productImageUploadService);
        }
    }

    static class RollbackUploadService {

        private final ProductImageUploadService productImageUploadService;

        RollbackUploadService(ProductImageUploadService productImageUploadService) {
            this.productImageUploadService = productImageUploadService;
        }

        @Transactional
        public void uploadAndRollback(
                Long memberId,
                MockMultipartFile file,
                Consumer<ProductImageUploadResponse> uploaded
        ) {
            ProductImageUploadResponse response = productImageUploadService.upload(memberId, file);
            uploaded.accept(response);
            throw new IllegalStateException("force rollback");
        }
    }
}
