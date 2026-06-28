package com.sweet.market.wishlist.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.wishlist.application.WishlistService;

@RestController
@RequestMapping("/api/products/{productId}/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping
    public ApiResponse<WishlistResponse> add(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(wishlistService.add(member.id(), productId));
    }

    @DeleteMapping
    public ApiResponse<WishlistResponse> remove(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(wishlistService.remove(member.id(), productId));
    }
}
