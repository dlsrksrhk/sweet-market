package com.sweet.market.discovery;

import com.sweet.market.discovery.api.ActiveEventResponse;
import com.sweet.market.discovery.cache.ActiveEventCache;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.admin.AdminProductService;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.operations.StoreCatalogCommandService;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveEventCacheTest extends IntegrationTestSupport {

    @Autowired
    private ActiveEventCache cache;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AdminProductService adminProductService;

    @Autowired
    private StoreCatalogCommandService storeCatalogCommandService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMembershipRepository storeMembershipRepository;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void 캐시를_비운다() {
        cache.invalidate();
    }

    @Test
    void 활성_이벤트_요약은_삼십초_동안_원본을_한번만_조회한다() {
        cache.invalidate();
        AtomicInteger loaderCalls = new AtomicInteger();

        cache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });
        cache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });

        assertThat(loaderCalls).hasValue(1);
    }

    @Test
    void 캐시_비활성화_프로필에서는_매번_원본을_조회한다() {
        ActiveEventCache disabledCache = new ActiveEventCache(false);
        AtomicInteger loaderCalls = new AtomicInteger();

        disabledCache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });
        disabledCache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });

        assertThat(loaderCalls).hasValue(2);
    }

    @Test
    void 캠페인종료_상품숨김_재고소진후_캐시를_비운다() {
        cache.invalidate();
        AtomicInteger loaderCalls = new AtomicInteger();

        cache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new DiscoveryInvalidationEvent()));
        cache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });

        assertThat(loaderCalls).hasValue(2);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
            status.setRollbackOnly();
        });
        cache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });

        assertThat(loaderCalls).hasValue(2);
    }

    @Test
    void 관리자_상품_숨김은_커밋후_활성_이벤트_캐시를_비운다() {
        ProductFixture product = 공개_상품을_준비한다("admin-cache-owner@example.com");
        AtomicInteger loaderCalls = 캐시를_채운다();

        adminProductService.hide(product.productId());

        캐시를_다시_조회한다(loaderCalls);
        assertThat(loaderCalls).hasValue(2);
    }

    @Test
    void 상점_상품_숨김과_노출은_커밋후_활성_이벤트_캐시를_비운다() {
        ProductFixture product = 공개_상품을_준비한다("store-catalog-cache-owner@example.com");
        AtomicInteger loaderCalls = 캐시를_채운다();

        storeCatalogCommandService.hide(product.ownerId(), product.storeId(), List.of(product.productId()));
        캐시를_다시_조회한다(loaderCalls);
        assertThat(loaderCalls).hasValue(2);

        storeCatalogCommandService.show(product.ownerId(), product.storeId(), List.of(product.productId()));
        캐시를_다시_조회한다(loaderCalls);
        assertThat(loaderCalls).hasValue(3);
    }

    private ProductFixture 공개_상품을_준비한다(String email) {
        return transactionTemplate.execute(status -> {
            Member owner = memberRepository.save(Member.create(email, "encoded-password", "소유자"));
            Store store = Store.applyBusiness(owner, "캐시 상점", "소개", "법인", "123-45-67890");
            store.approve();
            store = storeRepository.save(store);
            storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
            Product product = productRepository.save(Product.create(store, "캐시 상품", "설명", 10_000L));
            return new ProductFixture(owner.getId(), store.getId(), product.getId());
        });
    }

    private AtomicInteger 캐시를_채운다() {
        cache.invalidate();
        AtomicInteger loaderCalls = new AtomicInteger();
        캐시를_다시_조회한다(loaderCalls);
        return loaderCalls;
    }

    private void 캐시를_다시_조회한다(AtomicInteger loaderCalls) {
        cache.get(() -> {
            loaderCalls.incrementAndGet();
            return List.<ActiveEventResponse>of();
        });
    }

    private record ProductFixture(Long ownerId, Long storeId, Long productId) {
    }
}
