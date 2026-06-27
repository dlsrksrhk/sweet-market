package com.sweet.market.product.domain;

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

@Getter
@Entity
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int sortOrder;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean representative;

    @Column(length = 255)
    private String storedFileName;

    @Column(length = 255)
    private String originalFileName;

    @Column(length = 100)
    private String contentType;

    private Long size;

    private ProductImage(
            String imageUrl,
            int sortOrder,
            boolean representative,
            String storedFileName,
            String originalFileName,
            String contentType,
            Long size
    ) {
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.representative = representative;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.size = size;
    }

    public static ProductImage create(String imageUrl) {
        return legacyUrl(imageUrl, 0, true);
    }

    public static ProductImage legacyUrl(String imageUrl, int sortOrder, boolean representative) {
        return new ProductImage(imageUrl, sortOrder, representative, null, null, null, null);
    }

    public static ProductImage local(
            String imageUrl,
            String storedFileName,
            String originalFileName,
            String contentType,
            long size,
            int sortOrder,
            boolean representative
    ) {
        return new ProductImage(
                imageUrl,
                sortOrder,
                representative,
                storedFileName,
                originalFileName,
                contentType,
                size
        );
    }

    void assignProduct(Product product) {
        this.product = product;
    }

    public void changeArrangement(int sortOrder, boolean representative) {
        this.sortOrder = sortOrder;
        this.representative = representative;
    }

    public boolean isLocalFile() {
        return storedFileName != null && !storedFileName.isBlank();
    }
}
