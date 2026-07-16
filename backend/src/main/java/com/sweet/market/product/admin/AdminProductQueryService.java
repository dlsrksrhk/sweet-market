package com.sweet.market.product.admin;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProductQueryService {

    private final ProductRepository productRepository;

    public AdminProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminProductSummaryResponse> search(AdminProductSearchRequest request, Pageable pageable) {
        return productRepository.searchAdminProducts(
                request.sellerId(),
                request.status(),
                request.normalizedKeyword(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminProductDetailResponse findDetail(Long productId) {
        Product product = productRepository.findWithStoreAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return AdminProductDetailResponse.from(product);
    }
}
