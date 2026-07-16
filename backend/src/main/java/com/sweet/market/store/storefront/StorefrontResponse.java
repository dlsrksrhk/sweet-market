package com.sweet.market.store.storefront;

import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record StorefrontResponse(
        Long storeId,
        StoreType type,
        String publicName,
        String introduction,
        StoreStatus operatingStatus,
        Double averageRating,
        long reviewCount,
        long publicProductCount
) {
    public StorefrontResponse {
        if (averageRating != null) {
            averageRating = BigDecimal.valueOf(averageRating)
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }
}
