package com.sweet.market.cart.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.cart.query.CartQueryService;
import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/me/cart")
public class CartQueryController {

    private final CartQueryService cartQueryService;

    public CartQueryController(CartQueryService cartQueryService) {
        this.cartQueryService = cartQueryService;
    }

    @GetMapping
    public ApiResponse<Page<CartItemResponse>> listMine(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(cartQueryService.findMine(member.id(), pageable));
    }
}
