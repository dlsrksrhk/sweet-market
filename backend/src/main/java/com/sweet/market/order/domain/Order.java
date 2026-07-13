package com.sweet.market.order.domain;

import java.time.LocalDateTime;

import com.sweet.market.common.domain.error.DomainException;
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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_seller_id", columnList = "seller_id"))
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime confirmedAt;

    private Order(Member buyer, Product product, Member seller, OrderStatus status, LocalDateTime orderedAt) {
        this.buyer = buyer;
        this.product = product;
        this.seller = seller;
        this.status = status;
        this.orderedAt = orderedAt;
    }

    public static Order create(Member buyer, Product product) {
        if (!product.isPurchasable()) {
            throw new DomainException(OrderDomainError.PRODUCT_NOT_PURCHASABLE);
        }
        if (product.isSingleItem()) {
            product.reserve();
        }
        return new Order(buyer, product, product.getStore().getOwnerMember(), OrderStatus.CREATED, LocalDateTime.now());
    }

    public void cancel() {
        if (status == OrderStatus.CANCELED) {
            return;
        }
        if (status != OrderStatus.CREATED) {
            throw new DomainException(OrderDomainError.CANCELLATION_NOT_ALLOWED);
        }
        if (product.isSingleItem()) {
            product.restoreOnSaleFromReservation();
        }
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void markPaid() {
        if (status != OrderStatus.CREATED) {
            throw new DomainException(OrderDomainError.PAYMENT_NOT_ALLOWED);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancelPaidOrder() {
        if (status == OrderStatus.CANCELED) {
            return;
        }
        if (status != OrderStatus.PAID) {
            throw new DomainException(OrderDomainError.PAID_ORDER_CANCELLATION_NOT_ALLOWED);
        }
        if (product.isSingleItem()) {
            product.restoreOnSaleFromReservation();
        }
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void startShipping() {
        if (status != OrderStatus.PAID) {
            throw new DomainException(OrderDomainError.SHIPPING_NOT_ALLOWED);
        }
        this.status = OrderStatus.SHIPPING;
    }

    public void completeDelivery() {
        if (status != OrderStatus.SHIPPING) {
            throw new DomainException(OrderDomainError.DELIVERY_COMPLETION_NOT_ALLOWED);
        }
        this.status = OrderStatus.DELIVERED;
    }

    public void requestRefund() {
        if (status != OrderStatus.DELIVERED) {
            throw new DomainException(OrderDomainError.REFUND_REQUEST_NOT_ALLOWED);
        }
        this.status = OrderStatus.REFUND_REQUESTED;
    }

    public void markRefunded() {
        if (status != OrderStatus.REFUND_REQUESTED) {
            throw new DomainException(OrderDomainError.REFUND_NOT_ALLOWED);
        }
        this.status = OrderStatus.REFUNDED;
    }

    public void rejectRefund() {
        if (status != OrderStatus.REFUND_REQUESTED) {
            throw new DomainException(OrderDomainError.REFUND_REJECTION_NOT_ALLOWED);
        }
        this.status = OrderStatus.DELIVERED;
    }

    public void confirm() {
        if (status != OrderStatus.DELIVERED) {
            throw new DomainException(OrderDomainError.CONFIRMATION_NOT_ALLOWED);
        }
        if (product.isSingleItem()) {
            product.markSoldOutFromReservation();
        }
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long memberId) {
        return buyer.getId().equals(memberId);
    }
}
