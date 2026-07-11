package com.sweet.market.product.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sweet.market.member.domain.Member;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreStatus;

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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products", indexes = @Index(name = "idx_products_store_status_id", columnList = "store_id, status, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

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

    private Product(Store store, String title, String description, long price, ProductStatus status) {
        this.store = store;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = status;
    }

    public static Product create(Store store, String title, String description, long price) {
        return new Product(store, title, description, price, ProductStatus.ON_SALE);
    }

    /**
     * Compatibility factory for in-memory fixtures. Application commands must select an existing store.
     */
    public static Product create(Member owner, String title, String description, long price) {
        return create(Store.createPersonal(owner, owner.getNickname() + "의 상점", ""), title, description, price);
    }

    public void update(String title, String description, long price) {
        validateNotReserved();
        this.title = title;
        this.description = description;
        this.price = price;
    }

    public void hide() {
        validateNotReserved();
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

    public void markSoldOutFromReservation() {
        if (status != ProductStatus.RESERVED) {
            throw new IllegalStateException("Product is not reserved: " + status);
        }
        this.status = ProductStatus.SOLD_OUT;
    }

    public boolean isOwnedBy(Long memberId) {
        return store.getOwnerMember().getId().equals(memberId);
    }

    public boolean isPurchasable() {
        return status == ProductStatus.ON_SALE && store.getStatus() == StoreStatus.ACTIVE;
    }

    public List<ProductImage> getImages() {
        sortImages();
        return Collections.unmodifiableList(images);
    }

    public ProductImage addImage(String imageUrl) {
        return addLegacyImage(imageUrl);
    }

    public ProductImage addLegacyImage(String imageUrl) {
        validateNotReserved();
        if (images.size() >= 10) {
            throw new IllegalArgumentException("Product image limit exceeded");
        }
        ProductImage image = ProductImage.legacyUrl(imageUrl, nextSortOrder(), images.isEmpty());
        image.assignProduct(this);
        images.add(image);
        sortImages();
        return image;
    }

    public void replaceImages(List<ProductImage> nextImages) {
        validateNotReserved();
        validateImages(nextImages);
        images.clear();
        nextImages.forEach(image -> {
            image.assignProduct(this);
            images.add(image);
        });
        sortImages();
    }

    public void removeImage(Long imageId) {
        validateNotReserved();
        ProductImage target = images.stream()
                .filter(image -> imageId.equals(image.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Product image not found: " + imageId));
        if (images.size() == 1) {
            throw new IllegalStateException("Product image is required");
        }
        images.remove(target);
        if (target.isRepresentative()) {
            promoteRepresentative();
        }
    }

    private int nextSortOrder() {
        return images.stream()
                .mapToInt(ProductImage::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private void promoteRepresentative() {
        boolean hasRepresentative = images.stream()
                .anyMatch(ProductImage::isRepresentative);
        if (hasRepresentative) {
            return;
        }
        sortImages();
        ProductImage firstImage = images.get(0);
        firstImage.changeArrangement(firstImage.getSortOrder(), true);
    }

    private void validateImages(List<ProductImage> nextImages) {
        if (nextImages.isEmpty()) {
            throw new IllegalArgumentException("Product image is required");
        }
        if (nextImages.size() > 10) {
            throw new IllegalArgumentException("Product image limit exceeded");
        }
        long representativeCount = nextImages.stream()
                .filter(ProductImage::isRepresentative)
                .count();
        if (representativeCount != 1) {
            throw new IllegalArgumentException("Product representative image must be exactly one");
        }
        Set<Integer> sortOrders = new HashSet<>();
        boolean hasDuplicateSortOrder = nextImages.stream()
                .map(ProductImage::getSortOrder)
                .anyMatch(sortOrder -> !sortOrders.add(sortOrder));
        if (hasDuplicateSortOrder) {
            throw new IllegalArgumentException("Product image sort order must be unique");
        }
    }

    private void sortImages() {
        images.sort(Comparator.comparingInt(ProductImage::getSortOrder));
    }

    private void validateNotReserved() {
        if (status == ProductStatus.RESERVED) {
            throw new IllegalStateException("Reserved product cannot be changed");
        }
    }
}
