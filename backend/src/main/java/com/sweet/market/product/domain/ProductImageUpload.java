package com.sweet.market.product.domain;

import com.sweet.market.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "product_image_uploads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private Member uploader;

    @Column(nullable = false, length = 120)
    private String storedFileName;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 500)
    private String previewUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private ProductImageUpload(
            Member uploader,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            String previewUrl,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this.uploader = uploader;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.size = size;
        this.previewUrl = previewUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static ProductImageUpload create(
            Member uploader,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            String previewUrl,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        return new ProductImageUpload(
                uploader,
                storedFileName,
                originalFileName,
                contentType,
                size,
                previewUrl,
                createdAt,
                expiresAt
        );
    }

    public boolean isOwnedBy(Long memberId) {
        return uploader.getId().equals(memberId);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
