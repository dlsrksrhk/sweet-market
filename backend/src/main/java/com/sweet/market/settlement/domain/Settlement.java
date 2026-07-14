package com.sweet.market.settlement.domain;

import java.time.LocalDateTime;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "settlements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(nullable = false)
    private LocalDateTime settledAt;

    private Settlement(Order order, Member seller, long amount, SettlementStatus status, LocalDateTime settledAt) {
        this.order = order;
        this.seller = seller;
        this.amount = amount;
        this.status = status;
        this.settledAt = settledAt;
    }

    public static Settlement create(Order order) {
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new DomainException(SettlementDomainError.ORDER_NOT_CONFIRMED);
        }
        return new Settlement(
                order,
                order.getSeller(),
                order.getFinalPrice(),
                SettlementStatus.COMPLETED,
                LocalDateTime.now()
        );
    }

    public boolean isOwnedBy(Long memberId) {
        return seller.getId().equals(memberId);
    }
}
