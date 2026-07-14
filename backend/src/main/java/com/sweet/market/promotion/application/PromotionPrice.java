package com.sweet.market.promotion.application;

public record PromotionPrice(
        long listPrice,
        Long promotionId,
        String promotionTitle,
        long promotionDiscountAmount,
        long effectivePrice
) {

    public static PromotionPrice withoutPromotion(long listPrice) {
        return new PromotionPrice(listPrice, null, null, 0L, listPrice);
    }
}
