package com.sweet.market.store.api;

import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;

public record StorePrivateResponse(
        Long storeId,
        StoreType type,
        String publicName,
        String introduction,
        StoreStatus status,
        String legalBusinessName,
        String businessRegistrationId,
        String rejectionReason
) {
    public static StorePrivateResponse from(Store store) {
        return new StorePrivateResponse(
                store.getId(), store.getType(), store.getPublicName(), store.getIntroduction(), store.getStatus(),
                store.getLegalBusinessName(), store.getBusinessRegistrationId(), store.getRejectionReason()
        );
    }
}
