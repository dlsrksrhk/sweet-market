package com.sweet.market.product.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_image_uploads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

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
    private LocalDateTime expiresAt;

    private ProductImageUpload(
            Member member,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            String previewUrl,
            LocalDateTime expiresAt
    ) {
        this.member = member;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.size = size;
        this.previewUrl = previewUrl;
        this.expiresAt = expiresAt;
    }

    public static ProductImageUpload create(
            Member member,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            String previewUrl,
            LocalDateTime expiresAt
    ) {
        return new ProductImageUpload(member, storedFileName, originalFileName, contentType, size, previewUrl, expiresAt);
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
