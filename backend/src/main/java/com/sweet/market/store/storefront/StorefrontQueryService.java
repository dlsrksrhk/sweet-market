package com.sweet.market.store.storefront;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
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
    private final ProductRepository productRepository;

    public StorefrontQueryService(StoreRepository storeRepository, ProductRepository productRepository) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
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

    @Transactional(readOnly = true)
    public Page<StorefrontProductResponse> findProducts(
            long storeId,
            ProductStatus status,
            StorefrontProductSort sort,
            int page,
            int size,
            Long viewerId
    ) {
        validatePublicProductStatus(status);
        PageRequest pageRequest = PageRequest.of(page, size, productSort(sort));
        Page<StorefrontProductResponse> products = productRepository.findStorefrontProducts(
                storeId,
                status,
                viewerId,
                pageRequest
        );
        if (products.hasContent()) {
            return products;
        }
        Store store = storeRepository.findById(storeId)
                .filter(candidate -> PUBLIC_STORE_STATUSES.contains(candidate.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (store.getStatus() == StoreStatus.SUSPENDED) {
            return Page.empty(pageRequest);
        }
        return products;
    }

    private void validatePublicProductStatus(ProductStatus status) {
        if (!PUBLIC_PRODUCT_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private Sort productSort(StorefrontProductSort sort) {
        return switch (sort) {
            case NEWEST -> Sort.by(Sort.Order.desc("id"));
            case PRICE_ASC -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"));
        };
    }
}
