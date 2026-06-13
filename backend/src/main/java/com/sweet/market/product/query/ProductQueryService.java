package com.sweet.market.product.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findOnSaleProducts(Pageable pageable) {
        return productRepository.findByStatusOrderByIdDesc(ProductStatus.ON_SALE, pageable)
                .map(ProductSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findMine(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerIdOrderByIdDesc(sellerId, pageable)
                .map(ProductSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findOnSaleProduct(Long productId) {
        Product product = productRepository.findWithSellerAndImagesByIdAndStatus(productId, ProductStatus.ON_SALE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product);
    }
}
