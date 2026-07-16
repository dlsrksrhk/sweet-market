package com.sweet.market.cart.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.cart.application.CartService;
import com.sweet.market.common.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ApiResponse<CartResponse> add(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartService.add(member.id(), productId));
    }

    @DeleteMapping
    public ApiResponse<CartResponse> remove(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartService.remove(member.id(), productId));
    }
}
