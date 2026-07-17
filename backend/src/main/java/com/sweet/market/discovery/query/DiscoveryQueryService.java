package com.sweet.market.discovery.query;

import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.catalog.api.CatalogProductCardResponse;
import com.sweet.market.catalog.query.CatalogProductRow;
import com.sweet.market.discovery.api.ActiveEventResponse;
import com.sweet.market.discovery.cache.ActiveEventCache;
import com.sweet.market.discovery.api.EventDetailResponse;
import com.sweet.market.discovery.domain.DiscoveryEventType;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DiscoveryQueryService {

    private final DiscoveryRepository discoveryRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ActiveEventCache activeEventCache;
    private final Clock clock;

    @Autowired
    public DiscoveryQueryService(
            DiscoveryRepository discoveryRepository,
            WishlistItemRepository wishlistItemRepository,
            CartItemRepository cartItemRepository,
            ActiveEventCache activeEventCache,
            @Qualifier("discoveryClock") Clock clock
    ) {
        this.discoveryRepository = discoveryRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.activeEventCache = activeEventCache;
        this.clock = clock;
    }

    public DiscoveryQueryService(
            DiscoveryRepository discoveryRepository,
            WishlistItemRepository wishlistItemRepository,
            CartItemRepository cartItemRepository,
            ActiveEventCache activeEventCache
    ) {
        this(discoveryRepository, wishlistItemRepository, cartItemRepository, activeEventCache, Clock.systemUTC());
    }

    public DiscoveryQueryService(
            DiscoveryRepository discoveryRepository,
            WishlistItemRepository wishlistItemRepository,
            CartItemRepository cartItemRepository
    ) {
        this(discoveryRepository, wishlistItemRepository, cartItemRepository, new ActiveEventCache(), Clock.systemUTC());
    }

    @Transactional(readOnly = true)
    public List<ActiveEventResponse> activeEvents() {
        return activeEventCache.get(discoveryRepository::findActiveEvents);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse event(DiscoveryEventType eventType, Long eventId, Long viewerId) {
        EventDetailResponse event = discoveryRepository.findEvent(eventType, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new EventDetailResponse(
                event.eventType(), event.id(), event.title(), event.label(), event.storeId(), event.storeName(),
                event.representativeImageUrl(), event.endsAt(), catalogProductCards(discoveryRepository.findEventProducts(eventType, eventId), viewerId)
        );
    }

    @Transactional(readOnly = true)
    public List<CatalogProductCardResponse> popularProducts(Long viewerId) {
        OffsetDateTime since = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(7);
        return catalogProductCards(discoveryRepository.findPopularProducts(since), viewerId);
    }

    private List<CatalogProductCardResponse> catalogProductCards(List<CatalogProductRow> rows, Long viewerId) {
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
