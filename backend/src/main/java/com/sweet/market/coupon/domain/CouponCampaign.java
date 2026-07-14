package com.sweet.market.coupon.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.domain.Store;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coupon_campaigns", indexes = {
        @Index(name = "idx_coupon_campaigns_owner_lifecycle_issue_period", columnList = "owner_type, store_id, lifecycle_status, issue_starts_at, issue_ends_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20, updatable = false)
    private CouponCampaignOwnerType ownerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", updatable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CouponScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    @Column(name = "minimum_purchase_amount", nullable = false)
    private long minimumPurchaseAmount;

    @Column(nullable = false)
    private boolean stackable;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String label;

    @Column(name = "issue_starts_at", nullable = false)
    private Instant issueStartsAt;

    @Column(name = "issue_ends_at", nullable = false)
    private Instant issueEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "validity_type", nullable = false, length = 30)
    private CouponValidityType validityType;

    @Column(name = "common_expires_at")
    private Instant commonExpiresAt;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private CouponLifecycleStatus lifecycleStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CouponCampaignTarget> targets = new ArrayList<>();

    private CouponCampaign(
            CouponCampaignOwnerType ownerType,
            Store store,
            CouponScope scope,
            CouponDiscountType discountType,
            long discountValue,
            Long maxDiscountAmount,
            long minimumPurchaseAmount,
            boolean stackable,
            String title,
            String label,
            Instant issueStartsAt,
            Instant issueEndsAt,
            CouponValidityType validityType,
            Instant commonExpiresAt,
            Integer validityDays
    ) {
        this.ownerType = ownerType;
        this.store = store;
        this.scope = scope;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minimumPurchaseAmount = minimumPurchaseAmount;
        this.stackable = stackable;
        this.title = title;
        this.label = label;
        this.issueStartsAt = issueStartsAt;
        this.issueEndsAt = issueEndsAt;
        this.validityType = validityType;
        this.commonExpiresAt = commonExpiresAt;
        this.validityDays = validityDays;
        this.lifecycleStatus = CouponLifecycleStatus.DRAFT;
    }

    public static CouponCampaign create(
            CouponCampaignOwnerType ownerType,
            Store store,
            CouponScope scope,
            CouponDiscountType discountType,
            long discountValue,
            Long maxDiscountAmount,
            long minimumPurchaseAmount,
            boolean stackable,
            String title,
            String label,
            Instant issueStartsAt,
            Instant issueEndsAt,
            CouponValidityType validityType,
            Instant commonExpiresAt,
            Integer validityDays,
            List<Product> targets
    ) {
        validate(ownerType, store, scope, discountType, discountValue, maxDiscountAmount, minimumPurchaseAmount,
                issueStartsAt, issueEndsAt, validityType, commonExpiresAt, validityDays, targets);
        CouponCampaign campaign = new CouponCampaign(ownerType, store, scope, discountType, discountValue,
                maxDiscountAmount, minimumPurchaseAmount, stackable, title, label, issueStartsAt, issueEndsAt,
                validityType, commonExpiresAt, validityDays);
        campaign.replaceTargets(scope, targets);
        return campaign;
    }

    public CouponEffectiveStatus effectiveStatus(Instant now) {
        if (lifecycleStatus == CouponLifecycleStatus.PAUSED) {
            return CouponEffectiveStatus.PAUSED;
        }
        if (lifecycleStatus == CouponLifecycleStatus.ENDED || !now.isBefore(issueEndsAt)) {
            return CouponEffectiveStatus.ENDED;
        }
        return now.isBefore(issueStartsAt) ? CouponEffectiveStatus.SCHEDULED : CouponEffectiveStatus.ACTIVE;
    }

    public void schedule(Instant now) {
        if (lifecycleStatus != CouponLifecycleStatus.DRAFT || !now.isBefore(issueEndsAt)) {
            throw new DomainException(CouponDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = CouponLifecycleStatus.SCHEDULED;
    }

    public void pause(Instant now) {
        if (lifecycleStatus != CouponLifecycleStatus.SCHEDULED || effectiveStatus(now) == CouponEffectiveStatus.ENDED) {
            throw new DomainException(CouponDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = CouponLifecycleStatus.PAUSED;
    }

    public void resume(Instant now) {
        if (lifecycleStatus != CouponLifecycleStatus.PAUSED || !now.isBefore(issueEndsAt)) {
            throw new DomainException(CouponDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = CouponLifecycleStatus.SCHEDULED;
    }

    public void end() {
        if (lifecycleStatus == CouponLifecycleStatus.ENDED) {
            throw new DomainException(CouponDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
        lifecycleStatus = CouponLifecycleStatus.ENDED;
    }

    public void update(
            CouponScope scope,
            CouponDiscountType discountType,
            long discountValue,
            Long maxDiscountAmount,
            long minimumPurchaseAmount,
            boolean stackable,
            String title,
            String label,
            Instant issueStartsAt,
            Instant issueEndsAt,
            CouponValidityType validityType,
            Instant commonExpiresAt,
            Integer validityDays,
            List<Product> targets,
            Instant now
    ) {
        if (!canUpdate(now)) {
            throw new DomainException(CouponDomainError.UPDATE_NOT_ALLOWED);
        }
        validate(ownerType, store, scope, discountType, discountValue, maxDiscountAmount, minimumPurchaseAmount,
                issueStartsAt, issueEndsAt, validityType, commonExpiresAt, validityDays, targets);
        this.scope = scope;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minimumPurchaseAmount = minimumPurchaseAmount;
        this.stackable = stackable;
        this.title = title;
        this.label = label;
        this.issueStartsAt = issueStartsAt;
        this.issueEndsAt = issueEndsAt;
        this.validityType = validityType;
        this.commonExpiresAt = commonExpiresAt;
        this.validityDays = validityDays;
        replaceTargets(scope, targets);
    }

    public Instant resolveValidUntil(Instant issuedAt) {
        return validityType == CouponValidityType.COMMON_EXPIRY
                ? commonExpiresAt
                : issuedAt.plusSeconds(validityDays.longValue() * 86_400L);
    }

    public boolean isUsableForIssuedCoupon(Instant now) {
        return lifecycleStatus == CouponLifecycleStatus.SCHEDULED
                && effectiveStatus(now) == CouponEffectiveStatus.ACTIVE;
    }

    public void requireClaimable(Instant now) {
        if (!isUsableForIssuedCoupon(now)) {
            throw new DomainException(CouponDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
        }
    }

    public List<CouponCampaignTarget> getTargets() {
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
        CouponEffectiveStatus status = effectiveStatus(now);
        return status != CouponEffectiveStatus.ACTIVE
                && status != CouponEffectiveStatus.ENDED
                && lifecycleStatus != CouponLifecycleStatus.PAUSED
                && lifecycleStatus != CouponLifecycleStatus.ENDED;
    }

    private void replaceTargets(CouponScope scope, List<Product> products) {
        if (scope == CouponScope.ALL_PRODUCTS) {
            targets.clear();
            return;
        }
        Set<Object> nextProductKeys = products.stream()
                .map(CouponCampaign::targetKey)
                .collect(java.util.stream.Collectors.toSet());
        targets.removeIf(target -> !nextProductKeys.contains(targetKey(target.getProduct())));
        Set<Object> currentProductKeys = targets.stream()
                .map(target -> targetKey(target.getProduct()))
                .collect(java.util.stream.Collectors.toSet());
        products.stream()
                .filter(product -> !currentProductKeys.contains(targetKey(product)))
                .forEach(product -> {
                    CouponCampaignTarget target = new CouponCampaignTarget(product);
                    target.assignCampaign(this);
                    targets.add(target);
                });
    }

    private static void validate(
            CouponCampaignOwnerType ownerType,
            Store store,
            CouponScope scope,
            CouponDiscountType discountType,
            long discountValue,
            Long maxDiscountAmount,
            long minimumPurchaseAmount,
            Instant issueStartsAt,
            Instant issueEndsAt,
            CouponValidityType validityType,
            Instant commonExpiresAt,
            Integer validityDays,
            List<Product> targets
    ) {
        validateOwner(ownerType, store);
        validatePeriod(issueStartsAt, issueEndsAt);
        validateDiscount(discountType, discountValue, maxDiscountAmount, minimumPurchaseAmount);
        validateValidity(validityType, commonExpiresAt, validityDays, issueEndsAt);
        validateTargets(ownerType, store, scope, targets);
    }

    private static void validateOwner(CouponCampaignOwnerType ownerType, Store store) {
        if (ownerType == null
                || (ownerType == CouponCampaignOwnerType.PLATFORM && store != null)
                || (ownerType == CouponCampaignOwnerType.STORE && store == null)) {
            throw new DomainException(CouponDomainError.OWNER_STORE_INVALID);
        }
    }

    private static void validatePeriod(Instant issueStartsAt, Instant issueEndsAt) {
        if (issueStartsAt == null || issueEndsAt == null || !issueStartsAt.isBefore(issueEndsAt)) {
            throw new DomainException(CouponDomainError.INVALID_PERIOD);
        }
    }

    private static void validateDiscount(
            CouponDiscountType discountType,
            long discountValue,
            Long maxDiscountAmount,
            long minimumPurchaseAmount
    ) {
        if (discountType == null || discountValue <= 0) {
            throw new DomainException(CouponDomainError.INVALID_DISCOUNT_VALUE);
        }
        if (minimumPurchaseAmount < 0) {
            throw new DomainException(CouponDomainError.INVALID_MINIMUM_PURCHASE_AMOUNT);
        }
        boolean validMaximum = discountType == CouponDiscountType.PERCENTAGE
                ? maxDiscountAmount == null || maxDiscountAmount >= 0
                : maxDiscountAmount == null;
        if (!validMaximum) {
            throw new DomainException(CouponDomainError.MAX_DISCOUNT_AMOUNT_INVALID);
        }
    }

    private static void validateValidity(
            CouponValidityType validityType,
            Instant commonExpiresAt,
            Integer validityDays,
            Instant issueEndsAt
    ) {
        boolean commonExpiry = validityType == CouponValidityType.COMMON_EXPIRY
                && commonExpiresAt != null
                && validityDays == null
                && !commonExpiresAt.isBefore(issueEndsAt);
        boolean daysFromIssuance = validityType == CouponValidityType.DAYS_FROM_ISSUANCE
                && commonExpiresAt == null
                && validityDays != null
                && validityDays > 0;
        if (!commonExpiry && !daysFromIssuance) {
            throw new DomainException(CouponDomainError.INVALID_VALIDITY_POLICY);
        }
    }

    private static void validateTargets(
            CouponCampaignOwnerType ownerType,
            Store store,
            CouponScope scope,
            List<Product> products
    ) {
        List<Product> safeProducts = products == null ? List.of() : products;
        if (scope == null) {
            throw new DomainException(CouponDomainError.SELECTED_TARGET_REQUIRED);
        }
        if (scope == CouponScope.SELECTED_PRODUCTS && safeProducts.isEmpty()) {
            throw new DomainException(CouponDomainError.SELECTED_TARGET_REQUIRED);
        }
        if (scope == CouponScope.ALL_PRODUCTS && !safeProducts.isEmpty()) {
            throw new DomainException(CouponDomainError.ALL_PRODUCTS_TARGET_NOT_ALLOWED);
        }
        Set<Object> productKeys = new HashSet<>();
        boolean duplicateTarget = safeProducts.stream()
                .map(CouponCampaign::targetKey)
                .anyMatch(productKey -> !productKeys.add(productKey));
        if (duplicateTarget) {
            throw new DomainException(CouponDomainError.DUPLICATE_TARGET);
        }
        if (ownerType == CouponCampaignOwnerType.STORE
                && safeProducts.stream().anyMatch(product -> !belongsTo(store, product.getStore()))) {
            throw new DomainException(CouponDomainError.TARGET_STORE_MISMATCH);
        }
    }

    private static boolean belongsTo(Store expectedStore, Store targetStore) {
        if (expectedStore == targetStore) {
            return true;
        }
        return expectedStore != null
                && targetStore != null
                && expectedStore.getId() != null
                && Objects.equals(expectedStore.getId(), targetStore.getId());
    }

    private static Object targetKey(Product product) {
        return product.getId() == null ? product : product.getId();
    }
}
