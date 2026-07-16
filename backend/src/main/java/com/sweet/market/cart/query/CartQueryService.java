package com.sweet.market.cart.query;

import com.sweet.market.cart.api.CartItemResponse;
import com.sweet.market.cart.repository.CartItemReadRow;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.promotion.application.PromotionPrice;
import com.sweet.market.promotion.application.PromotionPricingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CartQueryService {

    private final CartItemRepository cartItemRepository;
    private final PromotionPricingService promotionPricingService;

    public CartQueryService(CartItemRepository cartItemRepository, PromotionPricingService promotionPricingService) {
        this.cartItemRepository = cartItemRepository;
        this.promotionPricingService = promotionPricingService;
    }

    @Transactional(readOnly = true)
    public Page<CartItemResponse> findMine(Long buyerId, Pageable pageable) {
        Pageable queryPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<CartItemReadRow> cartItems = cartItemRepository.findPageByBuyerId(buyerId, queryPageable);
        Map<Long, PromotionPrice> prices = promotionPricingService.quoteAll(cartItems.getContent().stream()
                .map(CartItemReadRow::productId)
                .toList());
        return cartItems.map(cartItem -> cartItem.toResponse().withPromotionPrice(
                prices.getOrDefault(cartItem.productId(), PromotionPrice.withoutPromotion(cartItem.price()))
        ));
    }
}
