package com.sweet.market.store.storefront;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.repository.StoreRepository;

@Service
public class StorefrontQueryService {

    private static final List<StoreStatus> PUBLIC_STORE_STATUSES = List.of(
            StoreStatus.ACTIVE,
            StoreStatus.SUSPENDED
    );
    private static final List<ProductStatus> PUBLIC_PRODUCT_STATUSES = List.of(
            ProductStatus.ON_SALE,
            ProductStatus.RESERVED,
            ProductStatus.SOLD_OUT
    );

    private final StoreRepository storeRepository;

    public StorefrontQueryService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public StorefrontResponse findStorefront(long storeId) {
        return storeRepository.findStorefrontHeader(
                        storeId,
                        PUBLIC_STORE_STATUSES,
                        StoreStatus.SUSPENDED,
                        PUBLIC_PRODUCT_STATUSES
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
