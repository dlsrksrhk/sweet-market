package com.sweet.market.review.api;

import com.sweet.market.review.domain.Review;

import java.time.LocalDateTime;

public record ProductReviewResponse(
        Long reviewId,
        Long orderId,
        Long productId,
        Long buyerId,
        String buyerNickname,
        int rating,
        String content,
        LocalDateTime createdAt
) {

    public static ProductReviewResponse from(Review review) {
        return new ProductReviewResponse(
                review.getId(),
                review.getOrder().getId(),
                review.getProduct().getId(),
                review.getBuyer().getId(),
                review.getBuyer().getNickname(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
