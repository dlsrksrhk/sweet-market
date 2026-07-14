package com.sweet.market.coupon.domain;

import com.sweet.market.product.domain.Product;

import jakarta.persistence.Entity;
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
@Table(name = "coupon_campaign_targets", uniqueConstraints = {
        @UniqueConstraint(name = "uq_coupon_campaign_targets_campaign_product", columnNames = {"coupon_campaign_id", "product_id"})
}, indexes = {
        @Index(name = "idx_coupon_campaign_targets_product_campaign", columnList = "product_id, coupon_campaign_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponCampaignTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_campaign_id", nullable = false)
    private CouponCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    CouponCampaignTarget(Product product) {
        this.product = product;
    }

    void assignCampaign(CouponCampaign campaign) {
        this.campaign = campaign;
    }
}
