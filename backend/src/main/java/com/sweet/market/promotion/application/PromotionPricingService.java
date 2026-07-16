package com.sweet.market.promotion.application;

import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.domain.PromotionCampaign;
import com.sweet.market.promotion.domain.PromotionScope;
import com.sweet.market.promotion.repository.PromotionCampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class PromotionPricingService {

    private final PromotionCampaignRepository promotionCampaignRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    @Autowired
    public PromotionPricingService(
            PromotionCampaignRepository promotionCampaignRepository,
            ProductRepository productRepository
    ) {
        this(promotionCampaignRepository, productRepository, Clock.systemUTC());
    }

    PromotionPricingService(
            PromotionCampaignRepository promotionCampaignRepository,
            ProductRepository productRepository,
            Clock clock
    ) {
        this.promotionCampaignRepository = promotionCampaignRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PromotionPrice quote(Product product) {
        return selectPrice(product, promotionCampaignRepository.findActiveApplicableByProductId(
                product.getStore().getId(), product.getId(), clock.instant()
        ));
    }

    @Transactional(readOnly = true)
    public Map<Long, PromotionPrice> quoteAll(Collection<Long> productIds) {
        List<Long> ids = productIds == null ? List.of() : productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(100)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Instant now = clock.instant();
        List<PromotionCampaign> campaigns = promotionCampaignRepository.findActiveApplicableByProductIds(ids, now);
        Map<Long, PromotionPrice> prices = new LinkedHashMap<>();
        productRepository.findAllWithStoreByIdIn(ids).forEach(product ->
                prices.put(product.getId(), selectPrice(product, campaigns))
        );
        return prices;
    }

    private PromotionPrice selectPrice(Product product, List<PromotionCampaign> campaigns) {
        return campaigns.stream()
                .filter(campaign -> appliesTo(campaign, product))
                .map(campaign -> new PricedCandidate(campaign, priceFor(campaign, product.getPrice())))
                .min(Comparator.comparingLong((PricedCandidate candidate) -> candidate.price().effectivePrice())
                        .thenComparing(Comparator.comparingInt((PricedCandidate candidate) -> candidate.campaign().getPriority()).reversed())
                        .thenComparing(candidate -> candidate.campaign().getId()))
                .map(PricedCandidate::price)
                .orElseGet(() -> PromotionPrice.withoutPromotion(product.getPrice()));
    }

    private boolean appliesTo(PromotionCampaign campaign, Product product) {
        if (!campaign.getStore().getId().equals(product.getStore().getId())) {
            return false;
        }
        return campaign.getScope() == PromotionScope.STORE_WIDE
                || campaign.getTargets().stream()
                .map(target -> target.getProduct().getId())
                .anyMatch(product.getId()::equals);
    }

    private PromotionPrice priceFor(PromotionCampaign campaign, long listPrice) {
        long discount = discount(campaign, listPrice);
        return new PromotionPrice(
                listPrice,
                campaign.getId(),
                campaign.getTitle(),
                discount,
                Math.max(0L, listPrice - discount)
        );
    }

    private long discount(PromotionCampaign campaign, long listPrice) {
        return switch (campaign.getDiscountType()) {
            case FIXED_AMOUNT -> Math.min(campaign.getDiscountValue(), listPrice);
            case PERCENTAGE -> percentageDiscount(campaign.getDiscountValue(), listPrice);
        };
    }

    private long percentageDiscount(long percentage, long listPrice) {
        if (percentage >= 100) {
            return listPrice;
        }
        return (listPrice / 100) * percentage + ((listPrice % 100) * percentage) / 100;
    }

    private record PricedCandidate(PromotionCampaign campaign, PromotionPrice price) {
    }
}
