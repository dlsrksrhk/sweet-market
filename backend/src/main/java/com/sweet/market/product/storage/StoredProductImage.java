package com.sweet.market.product.storage;

public record StoredProductImage(
        String storedFileName,
        String originalFileName,
        String contentType,
        long size,
        String url
) {
}
