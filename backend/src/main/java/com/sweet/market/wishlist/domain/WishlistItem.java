package com.sweet.market.wishlist.domain;

import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wishlist_items_buyer_product",
                columnNames = {"buyer_id", "product_id"}
        ),
        indexes = {
                @Index(name = "idx_wishlist_items_product_id", columnList = "product_id"),
                @Index(name = "idx_wishlist_items_buyer_created_id", columnList = "buyer_id, created_at, id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private WishlistItem(Member buyer, Product product) {
        this.buyer = buyer;
        this.product = product;
        this.createdAt = LocalDateTime.now();
    }

    public static WishlistItem create(Member buyer, Product product) {
        return new WishlistItem(buyer, product);
    }
}
