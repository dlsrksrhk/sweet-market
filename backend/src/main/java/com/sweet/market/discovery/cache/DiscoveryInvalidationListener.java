package com.sweet.market.discovery.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DiscoveryInvalidationListener {

    private final ActiveEventCache activeEventCache;

    public DiscoveryInvalidationListener(ActiveEventCache activeEventCache) {
        this.activeEventCache = activeEventCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(DiscoveryInvalidationEvent event) {
        activeEventCache.invalidate();
    }
}
