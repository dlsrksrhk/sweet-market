package com.sweet.market.store.operations;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.application.StoreAccessService;

@Service
public class StoreCatalogCommandService {

    private final StoreAccessService storeAccessService;
    private final ProductRepository productRepository;

    public StoreCatalogCommandService(
            StoreAccessService storeAccessService,
            ProductRepository productRepository
    ) {
        this.storeAccessService = storeAccessService;
        this.productRepository = productRepository;
    }

    @Transactional
    public void hide(Long memberId, Long storeId, List<Long> productIds) {
        List<Product> products = loadProducts(memberId, storeId, productIds);
        validateStatus(products, ProductStatus.ON_SALE);
        products.forEach(Product::hide);
    }

    @Transactional
    public void show(Long memberId, Long storeId, List<Long> productIds) {
        List<Product> products = loadProducts(memberId, storeId, productIds);
        validateStatus(products, ProductStatus.HIDDEN);
        products.forEach(Product::show);
    }

    private List<Product> loadProducts(Long memberId, Long storeId, List<Long> productIds) {
        if (new HashSet<>(productIds).size() != productIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        storeAccessService.requireCatalogOperator(memberId, storeId);
        List<Product> products = productRepository.findAllByStoreIdAndIdIn(storeId, productIds);
        if (products.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return products;
    }

    private void validateStatus(List<Product> products, ProductStatus requiredStatus) {
        boolean hasInvalidStatus = products.stream()
                .anyMatch(product -> product.getStatus() != requiredStatus);
        if (hasInvalidStatus) {
            throw new BusinessException(ErrorCode.PRODUCT_CHANGE_NOT_ALLOWED);
        }
    }
}
