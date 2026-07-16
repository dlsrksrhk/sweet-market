package com.sweet.market.review.domain;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_reviews_order", columnNames = "order_id"),
        indexes = {
                @Index(name = "idx_reviews_product_created_id", columnList = "product_id, created_at, id"),
                @Index(name = "idx_reviews_seller_id", columnList = "seller_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Review(Order order, int rating, String content, LocalDateTime createdAt) {
        this.order = order;
        this.buyer = order.getBuyer();
        this.product = order.getProduct();
        this.seller = order.getSeller();
        this.rating = rating;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static Review create(Order order, int rating, String content) {
        return new Review(order, rating, content, LocalDateTime.now());
    }
}
