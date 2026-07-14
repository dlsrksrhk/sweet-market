package com.sweet.market.catalog.query;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.catalog.api.CatalogProductCardResponse;
import com.sweet.market.catalog.api.CatalogSearchRequest;
import com.sweet.market.catalog.api.CatalogSearchResponse;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.store.domain.StoreStatus;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

@Service
public class CatalogSearchQueryService {

    private final CatalogSearchRepository catalogSearchRepository;
    private final CatalogCursorCodec catalogCursorCodec;
    private final StoreRepository storeRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CartItemRepository cartItemRepository;

    public CatalogSearchQueryService(
            CatalogSearchRepository catalogSearchRepository,
            CatalogCursorCodec catalogCursorCodec,
            StoreRepository storeRepository,
            WishlistItemRepository wishlistItemRepository,
            CartItemRepository cartItemRepository
    ) {
        this.catalogSearchRepository = catalogSearchRepository;
        this.catalogCursorCodec = catalogCursorCodec;
        this.storeRepository = storeRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public CatalogSearchResponse search(Long viewerId, CatalogSearchRequest request, Long fixedStoreId) {
        validateFixedStore(request, fixedStoreId);
        String fingerprint = request.filterFingerprint(fixedStoreId);
        CatalogCursor cursor = request.cursor() == null ? null : catalogCursorCodec.decode(request.cursor(), fingerprint);
        CatalogSearchCriteria criteria = CatalogSearchCriteria.from(request, fixedStoreId);
        List<CatalogProductRow> rows = catalogSearchRepository.findPage(criteria, cursor);
        boolean hasNext = rows.size() > criteria.size();
        List<CatalogProductRow> pageRows = hasNext ? rows.subList(0, criteria.size()) : rows;
        Set<Long> productIds = pageRows.stream().map(CatalogProductRow::productId).collect(java.util.stream.Collectors.toSet());
        Set<Long> wishlistedProductIds = viewerId == null || productIds.isEmpty() ? Set.of() : new HashSet<>(
                wishlistItemRepository.findProductIdsByBuyerIdAndProductIdIn(viewerId, productIds)
        );
        Set<Long> cartedProductIds = viewerId == null || productIds.isEmpty() ? Set.of() : new HashSet<>(
                cartItemRepository.findProductIdsByBuyerIdAndProductIdIn(viewerId, productIds)
        );
        List<CatalogProductCardResponse> content = pageRows.stream()
                .map(row -> response(row, wishlistedProductIds.contains(row.productId()), cartedProductIds.contains(row.productId())))
                .toList();
        String nextCursor = hasNext ? catalogCursorCodec.encode(cursorFor(pageRows.getLast(), criteria.sort(), fingerprint)) : null;
        return new CatalogSearchResponse(content, hasNext, nextCursor);
    }

    private void validateFixedStore(CatalogSearchRequest request, Long fixedStoreId) {
        if (fixedStoreId == null) {
            return;
        }
        if (request.storeId() != null || !storeRepository.existsByIdAndStatus(fixedStoreId, StoreStatus.ACTIVE)) {
            throw new BusinessException(request.storeId() == null ? ErrorCode.STORE_NOT_FOUND : ErrorCode.VALIDATION_ERROR);
        }
    }

    private CatalogProductCardResponse response(CatalogProductRow row, boolean wishlisted, boolean carted) {
        return new CatalogProductCardResponse(
                row.productId(), row.title(), row.price(), row.listPrice(), row.promotionId(), row.promotionTitle(),
                row.promotionDiscountAmount(), row.effectivePrice(), row.category(), row.representativeImageUrl(), row.availability(),
                row.salesPolicy(), row.storeId(), row.sellerId(), row.storeName(), row.storeType(), wishlisted, carted
        );
    }

    private CatalogCursor cursorFor(CatalogProductRow row, CatalogSort sort, String fingerprint) {
        return new CatalogCursor(
                sort,
                sort == CatalogSort.NEWEST ? null : row.effectivePrice(),
                row.productId(),
                fingerprint,
                catalogCursorCodec.expiresAt()
        );
    }
}
