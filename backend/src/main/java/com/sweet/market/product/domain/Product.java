package com.sweet.market.product.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sweet.market.member.domain.Member;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    private Product(Member seller, String title, String description, long price, ProductStatus status) {
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = status;
    }

    public static Product create(Member seller, String title, String description, long price) {
        return new Product(seller, title, description, price, ProductStatus.ON_SALE);
    }

    public void update(String title, String description, long price) {
        this.title = title;
        this.description = description;
        this.price = price;
    }

    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    public void reserve() {
        if (status != ProductStatus.ON_SALE) {
            throw new IllegalStateException("Product is not on sale: " + status);
        }
        this.status = ProductStatus.RESERVED;
    }

    public void restoreOnSaleFromReservation() {
        if (status != ProductStatus.RESERVED) {
            throw new IllegalStateException("Product is not reserved: " + status);
        }
        this.status = ProductStatus.ON_SALE;
    }

    public boolean isOwnedBy(Long memberId) {
        return seller.getId().equals(memberId);
    }

    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public ProductImage addImage(String imageUrl) {
        ProductImage image = ProductImage.create(imageUrl);
        image.assignProduct(this);
        images.add(image);
        return image;
    }

    public void removeImage(Long imageId) {
        boolean removed = images.removeIf(image -> imageId.equals(image.getId()));
        if (!removed) {
            throw new IllegalArgumentException("Product image not found: " + imageId);
        }
    }
}
