package com.sweet.market.inventory.domain;

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
@Table(name = "inventory_adjustments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_member_id", updatable = false)
    private Member actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false, length = 30)
    private InventoryChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 30)
    private InventoryAdjustmentReason reason;

    @Column(name = "reference_note", updatable = false, length = 500)
    private String referenceNote;

    @Column(name = "before_total_quantity", nullable = false, updatable = false)
    private int beforeTotalQuantity;

    @Column(name = "after_total_quantity", nullable = false, updatable = false)
    private int afterTotalQuantity;

    @Column(name = "before_reserved_quantity", nullable = false, updatable = false)
    private int beforeReservedQuantity;

    @Column(name = "after_reserved_quantity", nullable = false, updatable = false)
    private int afterReservedQuantity;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    private InventoryAdjustment(
            Inventory inventory,
            Order order,
            Member actor,
            InventoryChangeType changeType,
            InventoryAdjustmentReason reason,
            String referenceNote,
            int beforeTotalQuantity,
            int afterTotalQuantity,
            int beforeReservedQuantity,
            int afterReservedQuantity
    ) {
        this.inventory = inventory;
        this.product = inventory.getProduct();
        this.order = order;
        this.actor = actor;
        this.changeType = changeType;
        this.reason = reason;
        this.referenceNote = referenceNote;
        this.beforeTotalQuantity = beforeTotalQuantity;
        this.afterTotalQuantity = afterTotalQuantity;
        this.beforeReservedQuantity = beforeReservedQuantity;
        this.afterReservedQuantity = afterReservedQuantity;
        this.occurredAt = LocalDateTime.now();
    }

    static InventoryAdjustment initialization(Inventory inventory) {
        return new InventoryAdjustment(
                inventory,
                null,
                null,
                InventoryChangeType.INITIALIZATION,
                null,
                null,
                0,
                inventory.getTotalQuantity(),
                0,
                0
        );
    }

    static InventoryAdjustment reservation(Inventory inventory, Order order, int beforeReservedQuantity) {
        return reservation(inventory, order, beforeReservedQuantity, inventory.getReservedQuantity());
    }

    public static InventoryAdjustment reservation(
            Inventory inventory,
            Order order,
            int beforeReservedQuantity,
            int afterReservedQuantity
    ) {
        return new InventoryAdjustment(
                inventory,
                order,
                null,
                InventoryChangeType.RESERVATION,
                null,
                null,
                inventory.getTotalQuantity(),
                inventory.getTotalQuantity(),
                beforeReservedQuantity,
                afterReservedQuantity
        );
    }

    static InventoryAdjustment release(Inventory inventory, Order order, int beforeReservedQuantity) {
        return new InventoryAdjustment(
                inventory,
                order,
                null,
                InventoryChangeType.RELEASE,
                null,
                null,
                inventory.getTotalQuantity(),
                inventory.getTotalQuantity(),
                beforeReservedQuantity,
                inventory.getReservedQuantity()
        );
    }

    static InventoryAdjustment shipmentCommitment(
            Inventory inventory,
            Order order,
            int beforeTotalQuantity,
            int beforeReservedQuantity
    ) {
        return new InventoryAdjustment(
                inventory,
                order,
                null,
                InventoryChangeType.SHIPMENT_COMMITMENT,
                null,
                null,
                beforeTotalQuantity,
                inventory.getTotalQuantity(),
                beforeReservedQuantity,
                inventory.getReservedQuantity()
        );
    }

    static InventoryAdjustment manualAdjustment(
            Inventory inventory,
            int beforeTotalQuantity,
            InventoryAdjustmentReason reason,
            String referenceNote,
            Member actor
    ) {
        return new InventoryAdjustment(
                inventory,
                null,
                actor,
                InventoryChangeType.MANUAL_ADJUSTMENT,
                reason,
                referenceNote,
                beforeTotalQuantity,
                inventory.getTotalQuantity(),
                inventory.getReservedQuantity(),
                inventory.getReservedQuantity()
        );
    }
}
