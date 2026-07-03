package com.sweet.market.cart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.cart.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    Optional<CartItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    long deleteByBuyerIdAndProductId(Long buyerId, Long productId);
}
