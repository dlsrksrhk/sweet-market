package com.sweet.market.product.application;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductImageCleanupScheduler {

    private final ProductImageCleanupService cleanupService;

    public ProductImageCleanupScheduler(ProductImageCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${product.images.cleanup-cron:0 */10 * * * *}")
    public void cleanExpiredUploads() {
        cleanupService.cleanExpiredUploads(LocalDateTime.now());
    }
}
