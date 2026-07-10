package com.sweet.market.store.api;

import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreType;

public record PublicStoreResponse(
        Long storeId,
        StoreType type,
        String publicName,
        String introduction
) {
    public static PublicStoreResponse from(Store store) {
        return new PublicStoreResponse(store.getId(), store.getType(), store.getPublicName(), store.getIntroduction());
    }
}
