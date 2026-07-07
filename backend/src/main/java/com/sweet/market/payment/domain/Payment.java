package com.sweet.market.payment.domain;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, length = 100)
    private String externalPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime approvedAt;

    private LocalDateTime canceledAt;

    private Payment(Order order, String externalPaymentId, PaymentStatus status, LocalDateTime approvedAt) {
        this.order = order;
        this.externalPaymentId = externalPaymentId;
        this.status = status;
        this.approvedAt = approvedAt;
    }

    public static Payment approve(Order order, String externalPaymentId) {
        order.markPaid();
        return new Payment(order, externalPaymentId, PaymentStatus.APPROVED, LocalDateTime.now());
    }

    public void cancel() {
        if (status == PaymentStatus.CANCELED) {
            return;
        }
        if (status != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Payment cannot be canceled: " + status);
        }
        order.cancelPaidOrder();
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void refund() {
        if (status != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Payment cannot be refunded: " + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.canceledAt = LocalDateTime.now();
    }
}
