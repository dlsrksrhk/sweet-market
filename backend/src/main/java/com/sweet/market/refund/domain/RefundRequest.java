package com.sweet.market.refund.domain;

import java.time.LocalDateTime;

import com.sweet.market.common.domain.error.DomainException;
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
        validateOrderBuyer(order, buyer);
        validateText(reason, 10, 500, RefundRequestDomainError.REQUEST_REASON_REQUIRED,
                RefundRequestDomainError.REQUEST_REASON_LENGTH_INVALID);
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
        validateText(rejectReason, 5, 500, RefundRequestDomainError.REJECT_REASON_REQUIRED,
                RefundRequestDomainError.REJECT_REASON_LENGTH_INVALID);
        this.status = RefundRequestStatus.REJECTED;
        this.handledBy = handler;
        this.handledAt = LocalDateTime.now();
        this.rejectReason = rejectReason;
        this.order.rejectRefund();
    }

    public boolean isSellerOwnedBy(Long sellerId) {
        return order.getSeller().getId().equals(sellerId);
    }

    private void validateRequested() {
        if (status != RefundRequestStatus.REQUESTED) {
            throw new DomainException(RefundRequestDomainError.HANDLING_NOT_ALLOWED);
        }
    }

    private static void validateOrderBuyer(Order order, Member buyer) {
        if (order == null) {
            throw new DomainException(RefundRequestDomainError.ORDER_REQUIRED);
        }
        if (buyer == null) {
            throw new DomainException(RefundRequestDomainError.BUYER_REQUIRED);
        }
        Member orderBuyer = order.getBuyer();
        if (orderBuyer == buyer) {
            return;
        }
        if (orderBuyer.getId() != null && orderBuyer.getId().equals(buyer.getId())) {
            return;
        }
        throw new DomainException(RefundRequestDomainError.BUYER_ORDER_MISMATCH);
    }

    private static void validateText(
            String text,
            int minLength,
            int maxLength,
            RefundRequestDomainError requiredError,
            RefundRequestDomainError lengthInvalidError
    ) {
        if (text == null || text.isBlank()) {
            throw new DomainException(requiredError);
        }
        int length = text.trim().length();
        if (length < minLength || length > maxLength) {
            throw new DomainException(lengthInvalidError);
        }
    }
}
