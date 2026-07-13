package com.sweet.market.delivery.domain;

import java.time.LocalDateTime;

import com.sweet.market.common.domain.error.DomainException;
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
@Table(name = "deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private Delivery(Order order, String trackingNumber, DeliveryStatus status, LocalDateTime startedAt) {
        this.order = order;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.startedAt = startedAt;
    }

    public static Delivery start(Order order, String trackingNumber) {
        order.startShipping();
        return new Delivery(order, trackingNumber, DeliveryStatus.SHIPPING, LocalDateTime.now());
    }

    public void complete() {
        if (status != DeliveryStatus.SHIPPING) {
            throw new DomainException(DeliveryDomainError.COMPLETION_NOT_ALLOWED);
        }
        order.completeDelivery();
        this.status = DeliveryStatus.DELIVERED;
        this.completedAt = LocalDateTime.now();
    }
}
