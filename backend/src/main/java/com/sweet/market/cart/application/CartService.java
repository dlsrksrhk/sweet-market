package com.sweet.market.cart.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.cart.api.CartResponse;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate insertTransaction;

    public CartService(
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CartResponse add(Long buyerId, Long productId) {
        Product product = productRepository.findWithSellerById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (cartItemRepository.existsByBuyerIdAndProductId(buyerId, productId)) {
            return new CartResponse(productId, true);
        }

        validateCartable(buyerId, product);

        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        try {
            insertTransaction.executeWithoutResult(status ->
                    cartItemRepository.saveAndFlush(CartItem.create(buyer, product))
            );
        } catch (DataIntegrityViolationException exception) {
            if (cartItemRepository.findByBuyerIdAndProductId(buyerId, productId).isEmpty()) {
                throw exception;
            }
        }

        return new CartResponse(productId, true);
    }

    @Transactional
    public CartResponse remove(Long buyerId, Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        cartItemRepository.deleteByBuyerIdAndProductId(buyerId, productId);
        return new CartResponse(productId, false);
    }

    private void validateCartable(Long buyerId, Product product) {
        if (product.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.CART_OWN_PRODUCT_NOT_ALLOWED);
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.CART_PRODUCT_NOT_ON_SALE);
        }
    }
}
