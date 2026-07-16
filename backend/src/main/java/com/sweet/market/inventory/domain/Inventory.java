package com.sweet.market.inventory.domain;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "inventories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true, updatable = false)
    private Product product;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Transient
    private InventoryAdjustment initializationAdjustment;

    private Inventory(Product product, int totalQuantity) {
        validateTotalQuantity(totalQuantity);
        this.product = product;
        this.totalQuantity = totalQuantity;
        this.initializationAdjustment = InventoryAdjustment.initialization(this);
    }

    public static Inventory initialize(Product product, int totalQuantity) {
        if (product.isSingleItem()) {
            throw new DomainException(InventoryDomainError.SINGLE_ITEM_PRODUCT_NOT_SUPPORTED);
        }
        return new Inventory(product, totalQuantity);
    }

    public int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    public InventoryAdjustment reserve(Order order) {
        validateOrderProduct(order);
        if (getAvailableQuantity() == 0) {
            throw new DomainException(InventoryDomainError.STOCK_UNAVAILABLE);
        }
        int beforeReservedQuantity = reservedQuantity;
        reservedQuantity++;
        return InventoryAdjustment.reservation(this, order, beforeReservedQuantity);
    }

    public InventoryAdjustment release(Order order) {
        validateOrderProduct(order);
        if (reservedQuantity == 0) {
            throw new DomainException(InventoryDomainError.RESERVATION_NOT_FOUND);
        }
        int beforeReservedQuantity = reservedQuantity;
        reservedQuantity--;
        return InventoryAdjustment.release(this, order, beforeReservedQuantity);
    }

    public InventoryAdjustment commitShipment(Order order) {
        validateOrderProduct(order);
        if (reservedQuantity == 0) {
            throw new DomainException(InventoryDomainError.RESERVATION_NOT_FOUND);
        }
        int beforeTotalQuantity = totalQuantity;
        int beforeReservedQuantity = reservedQuantity;
        totalQuantity--;
        reservedQuantity--;
        return InventoryAdjustment.shipmentCommitment(this, order, beforeTotalQuantity, beforeReservedQuantity);
    }

    public InventoryAdjustment adjust(
            int totalQuantity,
            InventoryAdjustmentReason reason,
            String referenceNote,
            Member actor
    ) {
        validateTotalQuantity(totalQuantity);
        if (totalQuantity < reservedQuantity) {
            throw new DomainException(InventoryDomainError.TOTAL_BELOW_RESERVED_QUANTITY);
        }
        if (reason == null) {
            throw new DomainException(InventoryDomainError.ADJUSTMENT_REASON_REQUIRED);
        }
        if (actor == null) {
            throw new DomainException(InventoryDomainError.ADJUSTMENT_ACTOR_REQUIRED);
        }
        int beforeTotalQuantity = this.totalQuantity;
        this.totalQuantity = totalQuantity;
        return InventoryAdjustment.manualAdjustment(this, beforeTotalQuantity, reason, referenceNote, actor);
    }

    private static void validateTotalQuantity(int totalQuantity) {
        if (totalQuantity < 0) {
            throw new DomainException(InventoryDomainError.TOTAL_QUANTITY_NEGATIVE);
        }
    }

    private void validateOrderProduct(Order order) {
        if (order == null || order.getProduct() != product) {
            throw new DomainException(InventoryDomainError.ORDER_PRODUCT_MISMATCH);
        }
    }
}
