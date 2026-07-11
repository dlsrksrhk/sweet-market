package com.sweet.market.store.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.store.api.StorePrivateResponse;
import com.sweet.market.store.repository.StoreRepository;

@Service
public class StoreQueryService {

    private final StoreRepository storeRepository;

    public StoreQueryService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public List<StorePrivateResponse> findOwnedStores(Long memberId) {
        return storeRepository.findAllOwnedByOwnerMemberIdInMyStoreOrder(memberId).stream()
                .map(StorePrivateResponse::from)
                .toList();
    }
}
