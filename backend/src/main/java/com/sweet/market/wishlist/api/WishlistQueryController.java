package com.sweet.market.wishlist.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.wishlist.query.WishlistQueryService;

@RestController
@RequestMapping("/api/me/wishlist")
public class WishlistQueryController {

    private final WishlistQueryService wishlistQueryService;

    public WishlistQueryController(WishlistQueryService wishlistQueryService) {
        this.wishlistQueryService = wishlistQueryService;
    }

    @GetMapping
    public ApiResponse<Page<WishlistItemResponse>> listMine(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(wishlistQueryService.findMine(member.id(), pageable));
    }
}
