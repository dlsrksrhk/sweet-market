package com.sweet.market.review.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.review.api.ProductReviewResponse;
import com.sweet.market.review.repository.ReviewRepository;

@Service
public class ProductReviewQueryService {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;

    public ProductReviewQueryService(ProductRepository productRepository, ReviewRepository reviewRepository) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductReviewResponse> findByProductId(Long productId, Pageable pageable) {
        productRepository.findBuyerVisibleDetailById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, pageable)
                .map(ProductReviewResponse::from);
    }
}
