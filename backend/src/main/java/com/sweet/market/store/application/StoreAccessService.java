package com.sweet.market.store.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMemberRole;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;

@Service
public class StoreAccessService {

    private final StoreRepository storeRepository;
    private final StoreMembershipRepository storeMembershipRepository;

    public StoreAccessService(StoreRepository storeRepository, StoreMembershipRepository storeMembershipRepository) {
        this.storeRepository = storeRepository;
        this.storeMembershipRepository = storeMembershipRepository;
    }

    @Transactional(readOnly = true)
    public Store requireCatalogOperator(Long memberId, Long storeId) {
        Store store = findStore(storeId);
        StoreMembership membership = storeMembershipRepository.findByStoreIdAndMemberIdAndActiveTrue(storeId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_ACCESS_DENIED));
        if (store.getStatus() != com.sweet.market.store.domain.StoreStatus.ACTIVE
                || (membership.getRole() != StoreMemberRole.OWNER && membership.getRole() != StoreMemberRole.MANAGER)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return store;
    }

    @Transactional(readOnly = true)
    public Store requireOwner(Long memberId, Long storeId) {
        Store store = findStore(storeId);
        StoreMembership membership = storeMembershipRepository.findByStoreIdAndMemberIdAndActiveTrue(storeId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_OWNER_REQUIRED));
        if (membership.getRole() != StoreMemberRole.OWNER || !store.getOwnerMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.STORE_OWNER_REQUIRED);
        }
        return store;
    }

    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
