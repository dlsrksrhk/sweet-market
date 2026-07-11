package com.sweet.market.store.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.domain.StoreType;
import com.sweet.market.store.repository.StoreRepository;

@Service
public class AdminBusinessStoreQueryService {

    private final StoreRepository storeRepository;

    public AdminBusinessStoreQueryService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminBusinessStoreResponse> search(StoreStatus status, Pageable pageable) {
        Page<Store> stores = status == null
                ? storeRepository.findAllByType(StoreType.BUSINESS, pageable)
                : storeRepository.findAllByTypeAndStatus(StoreType.BUSINESS, status, pageable);
        return stores.map(AdminBusinessStoreResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminBusinessStoreResponse findDetail(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (store.getType() != StoreType.BUSINESS) {
            throw new BusinessException(ErrorCode.STORE_INVALID_TYPE);
        }
        return AdminBusinessStoreResponse.from(store);
    }
}
