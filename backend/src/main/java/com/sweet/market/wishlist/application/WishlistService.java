package com.sweet.market.wishlist.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.wishlist.api.WishlistResponse;
import com.sweet.market.wishlist.domain.WishlistItem;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate insertTransaction;

    public WishlistService(
            WishlistItemRepository wishlistItemRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public WishlistResponse add(Long buyerId, Long productId) {
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Product product = productRepository.findWithSellerById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateWishlistable(buyerId, product);

        if (wishlistItemRepository.existsByBuyerIdAndProductId(buyerId, productId)) {
            return response(productId, true);
        }

        try {
            insertTransaction.executeWithoutResult(status ->
                    wishlistItemRepository.saveAndFlush(WishlistItem.create(buyer, product))
            );
        } catch (DataIntegrityViolationException exception) {
            if (wishlistItemRepository.findByBuyerIdAndProductId(buyerId, productId).isEmpty()) {
                throw exception;
            }
        }
        return response(productId, true);
    }

    @Transactional
    public WishlistResponse remove(Long buyerId, Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        wishlistItemRepository.deleteByBuyerIdAndProductId(buyerId, productId);
        return response(productId, false);
    }

    private void validateWishlistable(Long buyerId, Product product) {
        if (product.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.WISHLIST_OWN_PRODUCT_NOT_ALLOWED);
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.WISHLIST_PRODUCT_NOT_ON_SALE);
        }
    }

    private WishlistResponse response(Long productId, boolean wishlisted) {
        return new WishlistResponse(productId, wishlisted, wishlistItemRepository.countByProductId(productId));
    }
}
