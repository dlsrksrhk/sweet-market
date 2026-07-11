package com.sweet.market.store.operations;

import com.sweet.market.store.domain.StoreMemberRole;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;

public record OperableStoreResponse(
        Long storeId,
        StoreType type,
        String publicName,
        StoreStatus status,
        StoreMemberRole role
) {
}
