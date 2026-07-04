package com.sweet.market.review.query;

public record ReviewSummary(
        long reviewCount,
        Double averageRating
) {

    public static ReviewSummary empty() {
        return new ReviewSummary(0, null);
    }
}
