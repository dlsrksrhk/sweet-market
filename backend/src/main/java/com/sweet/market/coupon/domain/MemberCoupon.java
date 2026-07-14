package com.sweet.market.coupon.domain;

import java.time.Instant;

import com.sweet.market.member.domain.Member;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member_coupons", uniqueConstraints = {
        @UniqueConstraint(name = "uq_member_coupons_campaign_member", columnNames = {"coupon_campaign_id", "member_id"})
}, indexes = {
        @Index(name = "idx_member_coupons_member_status_valid_until_id", columnList = "member_id, status, valid_until, id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_campaign_id", nullable = false, updatable = false)
    private CouponCampaign campaign;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20, updatable = false)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false, updatable = false)
    private long discountValue;

    @Column(name = "max_discount_amount", updatable = false)
    private Long maxDiscountAmount;

    @Column(name = "minimum_purchase_amount", nullable = false, updatable = false)
    private long minimumPurchaseAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private CouponScope scope;

    @Column(nullable = false, updatable = false)
    private boolean stackable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberCouponStatus status;

    private MemberCoupon(
            Member member,
            CouponCampaign campaign,
            Instant issuedAt,
            Instant validUntil,
            CouponDiscountType discountType,
            long discountValue,
            Long maxDiscountAmount,
            long minimumPurchaseAmount,
            CouponScope scope,
            boolean stackable
    ) {
        this.member = member;
        this.campaign = campaign;
        this.issuedAt = issuedAt;
        this.validUntil = validUntil;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minimumPurchaseAmount = minimumPurchaseAmount;
        this.scope = scope;
        this.stackable = stackable;
        this.status = MemberCouponStatus.ISSUED;
    }

    public static MemberCoupon issue(Member member, CouponCampaign campaign, Instant issuedAt) {
        return new MemberCoupon(member, campaign, issuedAt, campaign.resolveValidUntil(issuedAt),
                campaign.getDiscountType(), campaign.getDiscountValue(), campaign.getMaxDiscountAmount(),
                campaign.getMinimumPurchaseAmount(), campaign.getScope(), campaign.isStackable());
    }

    public MemberCouponStatus walletStatus(Instant now) {
        if (status == MemberCouponStatus.USED) {
            return MemberCouponStatus.USED;
        }
        if (!now.isBefore(validUntil)) {
            return MemberCouponStatus.EXPIRED;
        }
        if (!campaign.isUsableForIssuedCoupon(now)) {
            return MemberCouponStatus.UNAVAILABLE;
        }
        return MemberCouponStatus.ISSUED;
    }
}
