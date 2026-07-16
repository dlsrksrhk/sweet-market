package com.sweet.market.productview.application;

import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.productview.domain.ProductViewEvent;
import com.sweet.market.productview.repository.ProductViewDeduplicationRepository;
import com.sweet.market.productview.repository.ProductViewEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class ProductViewRecordingService {

    private static final Duration DEDUPLICATION_WINDOW = Duration.ofSeconds(3);

    private final ProductRepository productRepository;
    private final ProductViewDeduplicationRepository productViewDeduplicationRepository;
    private final ProductViewEventRepository productViewEventRepository;

    public ProductViewRecordingService(
            ProductRepository productRepository,
            ProductViewDeduplicationRepository productViewDeduplicationRepository,
            ProductViewEventRepository productViewEventRepository
    ) {
        this.productRepository = productRepository;
        this.productViewDeduplicationRepository = productViewDeduplicationRepository;
        this.productViewEventRepository = productViewEventRepository;
    }

    @Transactional
    public void record(Long productId, String rawVisitor, Instant now) {
        productRepository.findBuyerVisibleDetailById(productId).ifPresent(product -> {
            String visitorHash = hash(rawVisitor);
            int updated = productViewDeduplicationRepository.advanceLastCountedAt(
                    productId,
                    visitorHash,
                    now,
                    now.minus(DEDUPLICATION_WINDOW)
            );
            if (updated == 1) {
                productViewEventRepository.save(ProductViewEvent.create(product, visitorHash, now));
            }
        });
    }

    private String hash(String rawVisitor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawVisitor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
