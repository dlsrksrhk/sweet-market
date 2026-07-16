package com.sweet.market.wishlist.query;

import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.wishlist.api.WishlistItemResponse;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistQueryService {

    private static final List<ProductStatus> VISIBLE_STATUSES = List.of(
            ProductStatus.ON_SALE,
            ProductStatus.RESERVED,
            ProductStatus.SOLD_OUT
    );

    private final WishlistItemRepository wishlistItemRepository;

    public WishlistQueryService(WishlistItemRepository wishlistItemRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<WishlistItemResponse> findMine(Long buyerId, Pageable pageable) {
        return wishlistItemRepository.findPageByBuyerIdAndProductStatusIn(buyerId, VISIBLE_STATUSES, pageable);
    }
}
