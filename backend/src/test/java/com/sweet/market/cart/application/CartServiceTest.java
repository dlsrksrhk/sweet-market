package com.sweet.market.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.sweet.market.cart.api.CartResponse;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.application.PromotionPricingService;

class CartServiceTest {

    @Test
    void 중복_삽입_충돌이_발생하면_기존_장바구니_항목을_읽고_성공으로_응답한다() {
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PromotionPricingService promotionPricingService = mock(PromotionPricingService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        CartService cartService = new CartService(
                cartItemRepository,
                productRepository,
                memberRepository,
                orderRepository,
                inventoryService,
                promotionPricingService,
                transactionManager
        );
        Member buyer = member(1L, "buyer@example.com", "buyer");
        Member seller = member(2L, "seller@example.com", "seller");
        Product product = product(10L, seller);
        CartItem existingItem = CartItem.create(buyer, product);
        TransactionStatus transactionStatus = new SimpleTransactionStatus();

        when(memberRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(productRepository.findWithStoreById(10L)).thenReturn(Optional.of(product));
        when(inventoryService.isAvailableForOrder(product)).thenReturn(true);
        when(cartItemRepository.existsByBuyerIdAndProductId(1L, 10L)).thenReturn(false);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(cartItemRepository.saveAndFlush(any(CartItem.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate cart item"));
        when(cartItemRepository.findByBuyerIdAndProductId(1L, 10L)).thenReturn(Optional.of(existingItem));

        CartResponse response = cartService.add(1L, 10L);

        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.carted()).isTrue();
        verify(transactionManager).rollback(transactionStatus);
        verify(cartItemRepository).findByBuyerIdAndProductId(1L, 10L);
    }

    private Member member(Long id, String email, String nickname) {
        Member member = Member.create(email, "encoded-password", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Product product(Long id, Member seller) {
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2000000);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
