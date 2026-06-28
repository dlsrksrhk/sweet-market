package com.sweet.market.product.query;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.api.ProductResponse;
import com.sweet.market.product.api.ProductSummaryResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import com.sweet.market.wishlist.repository.WishlistItemRepository.WishlistProductCountProjection;

@Service
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final WishlistItemRepository wishlistItemRepository;

    public ProductQueryService(ProductRepository productRepository, WishlistItemRepository wishlistItemRepository) {
        this.productRepository = productRepository;
        this.wishlistItemRepository = wishlistItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findOnSaleProducts(Pageable pageable) {
        return findOnSaleProducts(null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findOnSaleProducts(Long viewerId, Pageable pageable) {
        Page<Product> products = productRepository.findByStatusOrderByIdDesc(ProductStatus.ON_SALE, pageable);
        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();
        Map<Long, Long> wishlistCounts = wishlistCounts(productIds);
        Set<Long> wishedProductIds = wishedProductIds(viewerId, productIds);

        return products.map(product -> ProductSummaryResponse.from(
                product,
                wishlistCounts.getOrDefault(product.getId(), 0L),
                wishedProductIds.contains(product.getId())
        ));
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
        return ProductResponse.from(product, wishlistCount, wishlisted);
    }

    private Map<Long, Long> wishlistCounts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return wishlistItemRepository.countByProductIds(productIds).stream()
                .collect(Collectors.toMap(
                        WishlistProductCountProjection::getProductId,
                        WishlistProductCountProjection::getCount
                ));
    }

    private Set<Long> wishedProductIds(Long viewerId, List<Long> productIds) {
        if (viewerId == null || productIds.isEmpty()) {
            return Collections.emptySet();
        }
        return wishlistItemRepository.findWishedProductIds(viewerId, productIds).stream()
                .collect(Collectors.toSet());
    }
}
