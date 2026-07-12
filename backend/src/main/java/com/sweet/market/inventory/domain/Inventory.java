package com.sweet.market.inventory.domain;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
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
            throw new IllegalArgumentException("Single-item product cannot have inventory");
        }
        return new Inventory(product, totalQuantity);
    }

    public int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    public InventoryAdjustment reserve(Order order) {
        validateOrderProduct(order);
        if (getAvailableQuantity() == 0) {
            throw new IllegalStateException("Inventory is unavailable");
        }
        int beforeReservedQuantity = reservedQuantity;
        reservedQuantity++;
        return InventoryAdjustment.reservation(this, order, beforeReservedQuantity);
    }

    public InventoryAdjustment release(Order order) {
        validateOrderProduct(order);
        if (reservedQuantity == 0) {
            throw new IllegalStateException("Inventory has no reservation to release");
        }
        int beforeReservedQuantity = reservedQuantity;
        reservedQuantity--;
        return InventoryAdjustment.release(this, order, beforeReservedQuantity);
    }

    public InventoryAdjustment commitShipment(Order order) {
        validateOrderProduct(order);
        if (reservedQuantity == 0) {
            throw new IllegalStateException("Inventory has no reservation to commit");
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
            throw new IllegalStateException("Inventory total cannot be lower than reservations");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Inventory adjustment reason is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("Inventory adjustment actor is required");
        }
        int beforeTotalQuantity = this.totalQuantity;
        this.totalQuantity = totalQuantity;
        return InventoryAdjustment.manualAdjustment(this, beforeTotalQuantity, reason, referenceNote, actor);
    }

    private static void validateTotalQuantity(int totalQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Inventory total quantity cannot be negative");
        }
    }

    private void validateOrderProduct(Order order) {
        if (order == null || order.getProduct() != product) {
            throw new IllegalArgumentException("Order does not belong to inventory product");
        }
    }
}
