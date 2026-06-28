package com.sweet.market.wishlist.api;

public record WishlistResponse(
        Long productId,
        boolean wishlisted,
        long wishlistCount
) {
}
