package com.sweet.market.discovery.query;

import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.catalog.api.CatalogProductCardResponse;
import com.sweet.market.catalog.query.CatalogProductRow;
import com.sweet.market.discovery.api.ActiveEventResponse;
import com.sweet.market.discovery.api.EventDetailResponse;
import com.sweet.market.discovery.domain.DiscoveryEventType;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DiscoveryQueryService {

    private final DiscoveryRepository discoveryRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CartItemRepository cartItemRepository;

    public DiscoveryQueryService(
            DiscoveryRepository discoveryRepository,
            WishlistItemRepository wishlistItemRepository,
            CartItemRepository cartItemRepository
    ) {
        this.discoveryRepository = discoveryRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public List<ActiveEventResponse> activeEvents() {
        return discoveryRepository.findActiveEvents();
    }

    @Transactional(readOnly = true)
    public EventDetailResponse event(DiscoveryEventType eventType, Long eventId) {
        return discoveryRepository.findEvent(eventType, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<CatalogProductCardResponse> popularProducts(Long viewerId) {
        List<CatalogProductRow> rows = discoveryRepository.findPopularProducts(OffsetDateTime.now().minusDays(7));
        Set<Long> productIds = rows.stream().map(CatalogProductRow::productId).collect(java.util.stream.Collectors.toSet());
        Set<Long> wishlisted = viewerId == null || productIds.isEmpty() ? Set.of() : new HashSet<>(
                wishlistItemRepository.findProductIdsByBuyerIdAndProductIdIn(viewerId, productIds)
        );
        Set<Long> carted = viewerId == null || productIds.isEmpty() ? Set.of() : new HashSet<>(
                cartItemRepository.findProductIdsByBuyerIdAndProductIdIn(viewerId, productIds)
        );
        return rows.stream().map(row -> new CatalogProductCardResponse(
                row.productId(), row.title(), row.price(), row.listPrice(), row.promotionId(), row.promotionTitle(),
                row.promotionDiscountAmount(), row.effectivePrice(), row.category(), row.representativeImageUrl(), row.availability(),
                row.salesPolicy(), row.storeId(), row.sellerId(), row.storeName(), row.storeType(),
                wishlisted.contains(row.productId()), carted.contains(row.productId())
        )).toList();
    }
}
