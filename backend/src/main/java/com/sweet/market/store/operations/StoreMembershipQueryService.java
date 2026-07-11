package com.sweet.market.store.operations;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.store.repository.StoreMembershipRepository;

@Service
public class StoreMembershipQueryService {

    private final StoreAccessService storeAccessService;
    private final StoreMembershipRepository storeMembershipRepository;

    public StoreMembershipQueryService(
            StoreAccessService storeAccessService,
            StoreMembershipRepository storeMembershipRepository
    ) {
        this.storeAccessService = storeAccessService;
        this.storeMembershipRepository = storeMembershipRepository;
    }

    @Transactional(readOnly = true)
    public List<StoreMembershipResponse> findActive(Long memberId, Long storeId) {
        storeAccessService.requireOwner(memberId, storeId);
        return storeMembershipRepository.findActiveByStoreId(storeId);
    }
}
