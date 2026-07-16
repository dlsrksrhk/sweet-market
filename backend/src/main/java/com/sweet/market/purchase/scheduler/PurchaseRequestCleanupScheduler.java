package com.sweet.market.purchase.scheduler;

import com.sweet.market.purchase.application.PurchaseRequestService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PurchaseRequestCleanupScheduler {

    private final PurchaseRequestService purchaseRequestService;

    public PurchaseRequestCleanupScheduler(PurchaseRequestService purchaseRequestService) {
        this.purchaseRequestService = purchaseRequestService;
    }

    @Scheduled(cron = "${market.purchase-request-cleanup.cron:0 0 0 * * *}")
    public void purgeCompletedRequests() {
        purchaseRequestService.purgeCompletedBefore(Instant.now());
    }
}
