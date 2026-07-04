package com.sweet.market.review.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.review.query.ProductReviewQueryService;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ProductReviewController {

    private final ProductReviewQueryService productReviewQueryService;

    public ProductReviewController(ProductReviewQueryService productReviewQueryService) {
        this.productReviewQueryService = productReviewQueryService;
    }

    @GetMapping
    public ApiResponse<Page<ProductReviewResponse>> list(
            @PathVariable Long productId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productReviewQueryService.findByProductId(productId, pageable));
    }
}
