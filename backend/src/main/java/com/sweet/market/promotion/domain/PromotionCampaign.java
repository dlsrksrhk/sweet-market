package com.sweet.market.promotion.domain;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.domain.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

@Getter
@Entity
@Table(name = "promotion_campaigns", indexes = {
        @Index(name = "idx_promotion_campaigns_store_lifecycle_period", columnList = "store_id, lifecycle_status, start_at, end_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, updatable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private PromotionDiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String label;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private PromotionLifecycleStatus lifecycleStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromotionTarget> targets = new ArrayList<>();

    private PromotionCampaign(
            Store store,
            PromotionScope scope,
            PromotionDiscountType discountType,
            long discountValue,
            int priority,
            String title,
            String label,
            Instant startAt,
            Instant endAt
    ) {
        this.store = store;
        this.scope = scope;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.priority = priority;
        this.title = title;
        this.label = label;
        this.startAt = startAt;
        this.endAt = endAt;
        this.lifecycleStatus = PromotionLifecycleStatus.DRAFT;
    }

    public static PromotionCampaign create(
            Store store,
            PromotionScope scope,
            PromotionDiscountType discountType,
            long discountValue,
            int priority,
            String title,
            String label,
            Instant startAt,
            Instant endAt,
            List<Product> products
    ) {
        validatePeriod(startAt, endAt);
        validateDiscountValue(discountValue);
        validateTargets(scope, products);

        PromotionCampaign campaign = new PromotionCampaign(
                store, scope, discountType, discountValue, priority, title, label, startAt, endAt
        );
        campaign.replaceTargets(scope, products);
        return campaign;
    }

    public PromotionEffectiveStatus effectiveStatus(Instant now) {
        if (lifecycleStatus == PromotionLifecycleStatus.PAUSED) {
            return PromotionEffectiveStatus.PAUSED;
        }
        if (lifecycleStatus == PromotionLifecycleStatus.ENDED || !now.isBefore(endAt)) {
            return PromotionEffectiveStatus.ENDED;
        }
        return now.isBefore(startAt) ? PromotionEffectiveStatus.SCHEDULED : PromotionEffectiveStatus.ACTIVE;
    }

    public void schedule(Instant now) {
        if (lifecycleStatus != PromotionLifecycleStatus.DRAFT || !now.isBefore(endAt)) {
            throw new DomainException(PromotionDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = PromotionLifecycleStatus.SCHEDULED;
    }

    public void pause(Instant now) {
        if (lifecycleStatus != PromotionLifecycleStatus.SCHEDULED || effectiveStatus(now) == PromotionEffectiveStatus.ENDED) {
            throw new DomainException(PromotionDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = PromotionLifecycleStatus.PAUSED;
    }

    public void resume(Instant now) {
        if (lifecycleStatus != PromotionLifecycleStatus.PAUSED || !now.isBefore(endAt)) {
            throw new DomainException(PromotionDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = PromotionLifecycleStatus.SCHEDULED;
    }

    public void end() {
        if (lifecycleStatus == PromotionLifecycleStatus.ENDED) {
            throw new DomainException(PromotionDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = PromotionLifecycleStatus.ENDED;
    }

    public void update(
            PromotionScope scope,
            PromotionDiscountType discountType,
            long discountValue,
            int priority,
            String title,
            String label,
            Instant startAt,
            Instant endAt,
            List<Product> products,
            Instant now
    ) {
        if (!canUpdate(now)) {
            throw new DomainException(PromotionDomainError.UPDATE_NOT_ALLOWED);
        }
        validatePeriod(startAt, endAt);
        validateDiscountValue(discountValue);
        validateTargets(scope, products);

        this.scope = scope;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.priority = priority;
        this.title = title;
        this.label = label;
        this.startAt = startAt;
        this.endAt = endAt;
        replaceTargets(scope, products);
    }

    public List<PromotionTarget> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    private boolean canUpdate(Instant now) {
        return effectiveStatus(now) != PromotionEffectiveStatus.ACTIVE
                && effectiveStatus(now) != PromotionEffectiveStatus.ENDED
                && lifecycleStatus != PromotionLifecycleStatus.PAUSED
                && lifecycleStatus != PromotionLifecycleStatus.ENDED;
    }

    private void replaceTargets(PromotionScope scope, List<Product> products) {
        if (scope == PromotionScope.STORE_WIDE) {
            targets.clear();
            return;
        }
        Set<Product> nextProducts = new HashSet<>(products);
        targets.removeIf(target -> !nextProducts.contains(target.getProduct()));
        Set<Product> currentProducts = targets.stream()
                .map(PromotionTarget::getProduct)
                .collect(java.util.stream.Collectors.toSet());
        products.stream()
                .filter(product -> !currentProducts.contains(product))
                .forEach(product -> {
                    PromotionTarget target = new PromotionTarget(product);
                    target.assignCampaign(this);
                    targets.add(target);
                });
    }

    private static void validatePeriod(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new DomainException(PromotionDomainError.INVALID_PERIOD);
        }
    }

    private static void validateDiscountValue(long discountValue) {
        if (discountValue < 0) {
            throw new DomainException(PromotionDomainError.INVALID_DISCOUNT_VALUE);
        }
    }

    private static void validateTargets(PromotionScope scope, List<Product> products) {
        List<Product> safeProducts = products == null ? List.of() : products;
        if (scope == PromotionScope.SELECTED_PRODUCTS && safeProducts.isEmpty()) {
            throw new DomainException(PromotionDomainError.SELECTED_TARGET_REQUIRED);
        }
        if (scope == PromotionScope.STORE_WIDE && !safeProducts.isEmpty()) {
            throw new DomainException(PromotionDomainError.STORE_WIDE_TARGET_NOT_ALLOWED);
        }
        Set<Product> distinctProducts = new HashSet<>(safeProducts);
        if (distinctProducts.size() != safeProducts.size()) {
            throw new DomainException(PromotionDomainError.DUPLICATE_TARGET);
        }
    }
}
