package com.sweet.market.cart.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.cart.api.CartItemResponse;
import com.sweet.market.cart.repository.CartItemRepository;

@Service
public class CartQueryService {

    private final CartItemRepository cartItemRepository;

    public CartQueryService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<CartItemResponse> findMine(Long buyerId, Pageable pageable) {
        return cartItemRepository.findPageByBuyerId(buyerId, pageable);
    }
}
