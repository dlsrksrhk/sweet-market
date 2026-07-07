package com.sweet.market.refund.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "refund_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_refund_requests_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_refund_requests_status_requested_at_id", columnList = "status, requested_at, id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private Member handledBy;

    private LocalDateTime handledAt;

    @Column(length = 500)
    private String rejectReason;

    private RefundRequest(Order order, Member buyer, String reason) {
        this.order = order;
        this.buyer = buyer;
        this.reason = reason;
        this.status = RefundRequestStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    public static RefundRequest request(Order order, Member buyer, String reason) {
        order.requestRefund();
        return new RefundRequest(order, buyer, reason);
    }

    public void approve(Member handler) {
        validateRequested();
        this.status = RefundRequestStatus.APPROVED;
        this.handledBy = handler;
        this.handledAt = LocalDateTime.now();
        this.order.markRefunded();
    }

    public void reject(Member handler, String rejectReason) {
        validateRequested();
        this.status = RefundRequestStatus.REJECTED;
        this.handledBy = handler;
        this.handledAt = LocalDateTime.now();
        this.rejectReason = rejectReason;
        this.order.rejectRefund();
    }

    public boolean isSellerOwnedBy(Long sellerId) {
        return order.getProduct().isOwnedBy(sellerId);
    }

    private void validateRequested() {
        if (status != RefundRequestStatus.REQUESTED) {
            throw new IllegalStateException("Refund request cannot be handled: " + status);
        }
    }
}
