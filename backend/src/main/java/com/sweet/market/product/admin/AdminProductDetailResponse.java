package com.sweet.market.product.admin;

import java.util.List;

import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;

public record AdminProductDetailResponse(
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        String description,
        List<String> imageUrls
) {

    public static AdminProductDetailResponse from(Product product) {
        List<String> imageUrls = product.getImages().stream()
                .map(image -> image.getImageUrl())
                .toList();
        String thumbnailUrl = product.getImages().stream()
                .filter(ProductImage::isRepresentative)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getImageUrl)
                .orElse(null);

        return new AdminProductDetailResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getTitle(),
                product.getPrice(),
                product.getStatus().name(),
                thumbnailUrl,
                product.getDescription(),
                imageUrls
        );
    }
}
