package com.sweet.market.product.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

@Service
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CartItemRepository cartItemRepository;

    public ProductQueryService(
            ProductRepository productRepository,
            WishlistItemRepository wishlistItemRepository,
            CartItemRepository cartItemRepository
    ) {
        this.productRepository = productRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findOnSaleProducts(Pageable pageable) {
        return findOnSaleProducts(null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findOnSaleProducts(Long viewerId, Pageable pageable) {
        return productRepository.findPublicSummariesByStatusOrderByIdDesc(ProductStatus.ON_SALE, viewerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findMine(Long sellerId, Pageable pageable) {
        return productRepository.findSummariesBySellerIdOrderByIdDesc(sellerId, pageable);
    }

    @Transactional(readOnly = true)
    public ProductResponse findOnSaleProduct(Long productId) {
        return findOnSaleProduct(productId, null);
    }

    @Transactional(readOnly = true)
    public ProductResponse findOnSaleProduct(Long productId, Long viewerId) {
        Product product = productRepository.findWithSellerAndImagesByIdAndStatus(productId, ProductStatus.ON_SALE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        long wishlistCount = wishlistItemRepository.countByProductId(productId);
        boolean wishlisted = viewerId != null && wishlistItemRepository.existsByBuyerIdAndProductId(viewerId, productId);
        boolean carted = viewerId != null && cartItemRepository.existsByBuyerIdAndProductId(viewerId, productId);
        return ProductResponse.from(product, wishlistCount, wishlisted, carted);
    }
}
