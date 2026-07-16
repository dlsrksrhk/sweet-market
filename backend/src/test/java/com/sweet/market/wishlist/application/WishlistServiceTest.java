package com.sweet.market.wishlist.application;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.wishlist.api.WishlistResponse;
import com.sweet.market.wishlist.domain.WishlistItem;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WishlistServiceTest {

    @Test
    void 중복_삽입_충돌이_발생하면_기존_찜을_읽고_성공으로_응답한다() {
        WishlistItemRepository wishlistItemRepository = mock(WishlistItemRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        WishlistService wishlistService = new WishlistService(
                wishlistItemRepository,
                productRepository,
                memberRepository,
                transactionManager
        );
        Member buyer = member(1L, "buyer@example.com", "buyer");
        Member seller = member(2L, "seller@example.com", "seller");
        Product product = product(10L, seller);
        WishlistItem existingItem = WishlistItem.create(buyer, product);
        TransactionStatus transactionStatus = new SimpleTransactionStatus();

        when(memberRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(productRepository.findWithStoreById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findBuyerAvailabilityByProductId(10L)).thenReturn(Optional.of(
                new BuyerAvailabilityResponse(
                        ProductSalesPolicy.SINGLE_ITEM,
                        ProductStatus.ON_SALE,
                        null,
                        null
                )
        ));
        when(wishlistItemRepository.existsByBuyerIdAndProductId(1L, 10L)).thenReturn(false);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(wishlistItemRepository.saveAndFlush(any(WishlistItem.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate wishlist item"));
        when(wishlistItemRepository.findByBuyerIdAndProductId(1L, 10L)).thenReturn(Optional.of(existingItem));
        when(wishlistItemRepository.countByProductId(10L)).thenReturn(1L);

        WishlistResponse response = wishlistService.add(1L, 10L);

        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.wishlisted()).isTrue();
        assertThat(response.wishlistCount()).isEqualTo(1);
        verify(transactionManager).rollback(transactionStatus);
        verify(wishlistItemRepository).findByBuyerIdAndProductId(1L, 10L);
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
