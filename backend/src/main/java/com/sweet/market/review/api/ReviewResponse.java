package com.sweet.market.review.api;

import com.sweet.market.review.domain.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        Long orderId,
        Long productId,
        Long buyerId,
        String buyerNickname,
        int rating,
        String content,
        LocalDateTime createdAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
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
