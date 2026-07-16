package com.sweet.market.productview.application;

import com.sweet.market.productview.repository.ProductViewDeduplicationRepository;
import com.sweet.market.productview.repository.ProductViewEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class ProductViewRetentionCleanupService {

    private static final Duration RETENTION_PERIOD = Duration.ofDays(7);

    private final ProductViewEventRepository productViewEventRepository;
    private final ProductViewDeduplicationRepository productViewDeduplicationRepository;
    private final Clock clock;

    @Autowired
    public ProductViewRetentionCleanupService(
            ProductViewEventRepository productViewEventRepository,
            ProductViewDeduplicationRepository productViewDeduplicationRepository
    ) {
        this(productViewEventRepository, productViewDeduplicationRepository, Clock.systemUTC());
    }

    public ProductViewRetentionCleanupService(
            ProductViewEventRepository productViewEventRepository,
            ProductViewDeduplicationRepository productViewDeduplicationRepository,
            Clock clock
    ) {
        this.productViewEventRepository = productViewEventRepository;
        this.productViewDeduplicationRepository = productViewDeduplicationRepository;
        this.clock = clock;
    }

    @Transactional
    public void cleanup() {
        Instant cutoff = clock.instant().minus(RETENTION_PERIOD);
        productViewEventRepository.deleteByViewedAtBefore(cutoff);
        productViewDeduplicationRepository.deleteByLastCountedAtBefore(cutoff);
    }
}
