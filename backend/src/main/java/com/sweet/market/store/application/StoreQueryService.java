package com.sweet.market.store.application;

import com.sweet.market.store.api.StorePrivateResponse;
import com.sweet.market.store.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
