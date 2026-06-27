package com.sweet.market.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.application.ProductImageCleanupService;
import com.sweet.market.product.domain.ProductImageUpload;
import com.sweet.market.product.repository.ProductImageUploadRepository;
import com.sweet.market.product.storage.ProductImageStorageProperties;
import com.sweet.market.support.IntegrationTestSupport;

class ProductImageCleanupServiceTest extends IntegrationTestSupport {

    @Autowired
    private ProductImageCleanupService cleanupService;

    @Autowired
    private ProductImageUploadRepository uploadRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductImageStorageProperties properties;

    @Test
    void 만료된_임시_업로드와_파일을_정리한다() throws Exception {
        Member seller = memberRepository.save(Member.create("seller-cleanup@example.com", "encoded-password", "seller"));
        Files.createDirectories(properties.tempPath());
        Path expiredFile = properties.tempPath().resolve("expired.jpg");
        Files.write(expiredFile, new byte[]{1, 2, 3});
        LocalDateTime now = LocalDateTime.now();
        uploadRepository.save(ProductImageUpload.create(
                seller,
                "expired.jpg",
                "expired.jpg",
                "image/jpeg",
                3L,
                "/uploads/products/temp/expired.jpg",
                now.minusHours(1),
                now.minusMinutes(1)
        ));

        int deletedCount = cleanupService.cleanExpiredUploads(now);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(uploadRepository.findAll()).isEmpty();
        assertThat(Files.exists(expiredFile)).isFalse();
    }
}
