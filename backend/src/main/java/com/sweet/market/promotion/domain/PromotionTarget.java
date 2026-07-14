package com.sweet.market.promotion.domain;

import com.sweet.market.product.domain.Product;

import jakarta.persistence.Column;
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
@Table(name = "promotion_targets", uniqueConstraints = {
        @UniqueConstraint(name = "uq_promotion_targets_campaign_product", columnNames = {"promotion_campaign_id", "product_id"})
}, indexes = {
        @Index(name = "idx_promotion_targets_product_campaign", columnList = "product_id, promotion_campaign_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_campaign_id", nullable = false)
    private PromotionCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    PromotionTarget(Product product) {
        this.product = product;
    }

    void assignCampaign(PromotionCampaign campaign) {
        this.campaign = campaign;
    }
}
