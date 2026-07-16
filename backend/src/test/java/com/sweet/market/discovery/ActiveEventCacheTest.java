package com.sweet.market.discovery;

import com.sweet.market.discovery.api.ActiveEventResponse;
import com.sweet.market.discovery.cache.ActiveEventCache;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
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
}
