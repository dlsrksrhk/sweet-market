package com.sweet.market.productview.domain;

import com.sweet.market.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "product_view_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductViewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "visitor_hash", nullable = false, columnDefinition = "char(64)")
    private String visitorHash;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    private ProductViewEvent(Product product, String visitorHash, Instant viewedAt) {
        this.product = product;
        this.visitorHash = visitorHash;
        this.viewedAt = viewedAt;
    }

    public static ProductViewEvent create(Product product, String visitorHash, Instant viewedAt) {
        return new ProductViewEvent(product, visitorHash, viewedAt);
    }
}
