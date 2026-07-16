package com.sweet.market.cart.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.cart.application.CartService;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/cart/checkout")
public class CartCheckoutController {

    private final CartService cartService;

    public CartCheckoutController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ApiResponse<CartCheckoutResponse> checkout(
            Authentication authentication,
            @Valid @RequestBody CartCheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartService.checkout(member.id(), request.cartItemIds(), idempotencyKey));
    }
}
