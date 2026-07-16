package com.sweet.market.product.storage;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageStorageServiceTest {

    @TempDir
    Path uploadRoot;

    private ProductImageStorageProperties properties;
    private ProductImageStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new ProductImageStorageProperties();
        properties.setUploadRoot(uploadRoot);
        storageService = new ProductImageStorageService(properties);
    }

    @Test
    void JPEG_콘텐츠_타입이어도_파일_시그니처가_다르면_거부한다() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> storageService.storeTemporary(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
    }

    @Test
    void 최대_크기를_초과한_상품_이미지는_거부한다() {
        properties.setMaxFileSize(DataSize.ofBytes(3));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        assertThatThrownBy(() -> storageService.storeTemporary(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
    }

    @Test
    void 임시_상품_이미지_삭제는_경로_이탈_파일명을_거부한다() {
        assertThatThrownBy(() -> storageService.deleteTemporary("../product.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_IMAGE_INVALID_FILE);
    }

    @Test
    void 저장소_속성은_null과_공백_설정값이면_기본값을_유지한다() {
        ProductImageStorageProperties defaultProperties = new ProductImageStorageProperties();

        defaultProperties.setUploadRoot(null);
        defaultProperties.setTempDir(" ");
        defaultProperties.setPublicDir(null);
        defaultProperties.setTempExpiration(null);
        defaultProperties.setMaxFileSize(null);

        assertThat(defaultProperties.getUploadRoot()).isEqualTo(Path.of("./.local/product-images"));
        assertThat(defaultProperties.getTempDir()).isEqualTo("temp");
        assertThat(defaultProperties.getPublicDir()).isEqualTo("public");
        assertThat(defaultProperties.getTempExpiration()).isEqualTo(Duration.ofMinutes(60));
        assertThat(defaultProperties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(5));
    }
}
