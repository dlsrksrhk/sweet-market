package com.sweet.market.product.domain;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@Getter
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_store_status_id", columnList = "store_id, status, id"),
        @Index(name = "idx_products_store_status_price_id", columnList = "store_id, status, price, id")
})
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
    @Column(nullable = false, length = 30)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_policy", nullable = false, length = 20, updatable = false)
    private ProductSalesPolicy salesPolicy;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    private Product(
            Store store,
            String title,
            String description,
            long price,
            ProductStatus status,
            ProductSalesPolicy salesPolicy,
            Integer lowStockThreshold,
            ProductCategory category
    ) {
        this.store = store;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = status;
        this.salesPolicy = salesPolicy;
        this.lowStockThreshold = lowStockThreshold;
        this.category = category;
    }

    public static Product create(Store store, String title, String description, long price) {
        return create(store, title, description, price, ProductSalesPolicy.SINGLE_ITEM, null, null);
    }

    public static Product create(
            Store store,
            String title,
            String description,
            long price,
            ProductSalesPolicy salesPolicy,
            Integer lowStockThreshold,
            Integer initialTotalQuantity
    ) {
        return create(store, title, description, price, salesPolicy, lowStockThreshold, initialTotalQuantity, ProductCategory.OTHER);
    }

    public static Product create(
            Store store,
            String title,
            String description,
            long price,
            ProductSalesPolicy salesPolicy,
            Integer lowStockThreshold,
            Integer initialTotalQuantity,
            ProductCategory category
    ) {
        validateSalesPolicy(salesPolicy, lowStockThreshold, initialTotalQuantity);
        return new Product(
                store,
                title,
                description,
                price,
                ProductStatus.ON_SALE,
                salesPolicy,
                lowStockThreshold,
                category == null ? ProductCategory.OTHER : category
        );
    }

    /**
     * Compatibility factory for in-memory fixtures. Application commands must select an existing store.
     */
    public static Product create(Member owner, String title, String description, long price) {
        return create(Store.createPersonal(owner, owner.getNickname() + "의 상점", ""), title, description, price);
    }

    public void update(String title, String description, long price) {
        update(title, description, price, null);
    }

    public void update(String title, String description, long price, ProductCategory category) {
        validateNotReserved();
        this.title = title;
        this.description = description;
        this.price = price;
        if (category != null) {
            this.category = category;
        }
    }

    public void changeLowStockThreshold(int lowStockThreshold) {
        if (salesPolicy != ProductSalesPolicy.STOCK_MANAGED) {
            throw new DomainException(ProductDomainError.STOCK_SETTINGS_UNAVAILABLE);
        }
        if (lowStockThreshold <= 0) {
            throw new DomainException(ProductDomainError.LOW_STOCK_THRESHOLD_INVALID);
        }
        this.lowStockThreshold = lowStockThreshold;
    }

    public void hide() {
        validateNotReserved();
        this.status = ProductStatus.HIDDEN;
    }

    public void show() {
        if (status != ProductStatus.HIDDEN) {
            throw new DomainException(ProductDomainError.NOT_HIDDEN);
        }
        status = ProductStatus.ON_SALE;
    }

    public void reserve() {
        if (status != ProductStatus.ON_SALE) {
            throw new DomainException(ProductDomainError.NOT_ON_SALE);
        }
        this.status = ProductStatus.RESERVED;
    }

    public void restoreOnSaleFromReservation() {
        if (status != ProductStatus.RESERVED) {
            throw new DomainException(ProductDomainError.NOT_RESERVED);
        }
        this.status = ProductStatus.ON_SALE;
    }

    public void markSoldOutFromReservation() {
        if (status != ProductStatus.RESERVED) {
            throw new DomainException(ProductDomainError.NOT_RESERVED);
        }
        this.status = ProductStatus.SOLD_OUT;
    }

    public boolean isOwnedBy(Long memberId) {
        return store.getOwnerMember().getId().equals(memberId);
    }

    public boolean isPurchasable() {
        return isVisibleForNewOrder();
    }

    public boolean isSingleItem() {
        return salesPolicy == ProductSalesPolicy.SINGLE_ITEM;
    }

    public boolean isVisibleForNewOrder() {
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
            throw new DomainException(ProductDomainError.IMAGE_LIMIT_EXCEEDED);
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
                .orElseThrow(() -> new DomainException(ProductDomainError.IMAGE_NOT_FOUND));
        if (images.size() == 1) {
            throw new DomainException(ProductDomainError.IMAGE_REQUIRED);
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
            throw new DomainException(ProductDomainError.IMAGE_REQUIRED);
        }
        if (nextImages.size() > 10) {
            throw new DomainException(ProductDomainError.IMAGE_LIMIT_EXCEEDED);
        }
        long representativeCount = nextImages.stream()
                .filter(ProductImage::isRepresentative)
                .count();
        if (representativeCount != 1) {
            throw new DomainException(ProductDomainError.REPRESENTATIVE_IMAGE_COUNT_INVALID);
        }
        Set<Integer> sortOrders = new HashSet<>();
        boolean hasDuplicateSortOrder = nextImages.stream()
                .map(ProductImage::getSortOrder)
                .anyMatch(sortOrder -> !sortOrders.add(sortOrder));
        if (hasDuplicateSortOrder) {
            throw new DomainException(ProductDomainError.IMAGE_SORT_ORDER_DUPLICATE);
        }
    }

    private void sortImages() {
        images.sort(Comparator.comparingInt(ProductImage::getSortOrder));
    }

    private void validateNotReserved() {
        if (status == ProductStatus.RESERVED) {
            throw new DomainException(ProductDomainError.CHANGE_NOT_ALLOWED);
        }
    }

    private static void validateSalesPolicy(
            ProductSalesPolicy salesPolicy,
            Integer lowStockThreshold,
            Integer initialTotalQuantity
    ) {
        if (salesPolicy == null) {
            throw new DomainException(ProductDomainError.SALES_POLICY_REQUIRED);
        }
        if (salesPolicy == ProductSalesPolicy.SINGLE_ITEM) {
            if (lowStockThreshold != null || initialTotalQuantity != null) {
                throw new DomainException(ProductDomainError.SINGLE_ITEM_STOCK_SETTINGS_INVALID);
            }
            return;
        }
        if (lowStockThreshold == null || lowStockThreshold <= 0) {
            throw new DomainException(ProductDomainError.LOW_STOCK_THRESHOLD_INVALID);
        }
        if (initialTotalQuantity == null || initialTotalQuantity < 0) {
            throw new DomainException(ProductDomainError.INITIAL_STOCK_QUANTITY_INVALID);
        }
    }
}
