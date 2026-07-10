package com.sweet.market.store.admin;

import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;

public record AdminBusinessStoreResponse(
        Long storeId,
        Long ownerMemberId,
        StoreType type,
        String publicName,
        String introduction,
        StoreStatus status,
        String legalBusinessName,
        String businessRegistrationId,
        String rejectionReason
) {
    public static AdminBusinessStoreResponse from(Store store) {
        return new AdminBusinessStoreResponse(
                store.getId(), store.getOwnerMember().getId(), store.getType(), store.getPublicName(), store.getIntroduction(),
                store.getStatus(), store.getLegalBusinessName(), store.getBusinessRegistrationId(), store.getRejectionReason()
        );
    }
}
