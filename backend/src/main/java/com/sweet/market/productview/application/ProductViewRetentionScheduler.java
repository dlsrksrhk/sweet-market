package com.sweet.market.productview.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductViewRetentionScheduler {

    private final ProductViewRetentionCleanupService cleanupService;

    public ProductViewRetentionScheduler(ProductViewRetentionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${product.view-retention-cleanup.cron:0 0 0 * * *}")
    public void cleanupExpiredViews() {
        cleanupService.cleanup();
    }
}
