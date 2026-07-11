package com.sweet.market.store.api;

import com.sweet.market.store.domain.StoreType;

public record PublicStoreResponse(
        Long storeId,
        StoreType type,
        String publicName,
        String introduction
) {
}
