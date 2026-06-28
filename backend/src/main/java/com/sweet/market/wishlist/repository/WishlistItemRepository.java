package com.sweet.market.wishlist.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.wishlist.domain.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    boolean existsByBuyerIdAndProductId(Long buyerId, Long productId);

    Optional<WishlistItem> findByBuyerIdAndProductId(Long buyerId, Long productId);

    long countByProductId(Long productId);

    long deleteByBuyerIdAndProductId(Long buyerId, Long productId);
}
