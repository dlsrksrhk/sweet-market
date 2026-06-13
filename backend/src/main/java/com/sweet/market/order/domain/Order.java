package com.sweet.market.order.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime confirmedAt;

    private Order(Member buyer, Product product, OrderStatus status, LocalDateTime orderedAt) {
        this.buyer = buyer;
        this.product = product;
        this.status = status;
        this.orderedAt = orderedAt;
    }

    public static Order create(Member buyer, Product product) {
        product.reserve();
        return new Order(buyer, product, OrderStatus.CREATED, LocalDateTime.now());
    }

    public void cancel() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Order cannot be canceled: " + status);
        }
        product.restoreOnSaleFromReservation();
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void markPaid() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Order cannot be paid: " + status);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancelPaidOrder() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Paid order cannot be canceled: " + status);
        }
        product.restoreOnSaleFromReservation();
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void startShipping() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Order cannot start shipping: " + status);
        }
        this.status = OrderStatus.SHIPPING;
    }

    public void completeDelivery() {
        if (status != OrderStatus.SHIPPING) {
            throw new IllegalStateException("Order cannot complete delivery: " + status);
        }
        this.status = OrderStatus.DELIVERED;
    }

    public void confirm() {
        if (status != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Order cannot be confirmed: " + status);
        }
        product.markSoldOutFromReservation();
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long memberId) {
        return buyer.getId().equals(memberId);
    }
}
