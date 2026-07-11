package com.sweet.market.store.storefront;

import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.StoreType;

public record StorefrontProductResponse(
        Long id,
        Long storeId,
        String storeName,
        StoreType storeType,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        ProductStatus status,
        String thumbnailUrl,
        long wishlistCount,
        boolean wishlisted,
        boolean carted
) {
}
