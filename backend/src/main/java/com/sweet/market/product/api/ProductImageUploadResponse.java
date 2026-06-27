package com.sweet.market.product.api;

import java.time.LocalDateTime;

import com.sweet.market.product.domain.ProductImageUpload;

public record ProductImageUploadResponse(
        Long id,
        String previewUrl,
        String originalFileName,
        String contentType,
        long size,
        LocalDateTime expiresAt
) {

    public static ProductImageUploadResponse from(ProductImageUpload upload) {
        return new ProductImageUploadResponse(
                upload.getId(),
                upload.getPreviewUrl(),
                upload.getOriginalFileName(),
                upload.getContentType(),
                upload.getSize(),
                upload.getExpiresAt()
        );
    }
}
