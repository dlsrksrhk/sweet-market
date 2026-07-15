package com.sweet.market.coupon.domain;

import java.time.Instant;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coupon_reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_coupon_id", nullable = false, updatable = false)
    private MemberCoupon memberCoupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponReservationStatus status;

    @Column(name = "reserved_at", nullable = false, updatable = false)
    private Instant reservedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    private CouponReservation(MemberCoupon memberCoupon, Order order, Instant reservedAt, Instant expiresAt) {
        this.memberCoupon = memberCoupon;
        this.order = order;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
        this.status = CouponReservationStatus.RESERVED;
    }

    public static CouponReservation reserve(MemberCoupon memberCoupon, Order order, Instant reservedAt, Instant expiresAt) {
        return new CouponReservation(memberCoupon, order, reservedAt, expiresAt);
    }

    public void consume(Instant now) {
        requireReserved();
        if (!now.isBefore(expiresAt)) {
            throw new DomainException(CouponDomainError.RESERVATION_EXPIRED);
        }
        this.status = CouponReservationStatus.CONSUMED;
        this.consumedAt = now;
    }

    public void release(Instant now) {
        requireReserved();
        this.status = CouponReservationStatus.RELEASED;
        this.releasedAt = now;
    }

    public void expire(Instant now) {
        requireReserved();
        this.status = CouponReservationStatus.EXPIRED;
        this.releasedAt = now;
    }

    private void requireReserved() {
        if (status != CouponReservationStatus.RESERVED) {
            throw new DomainException(CouponDomainError.RESERVATION_TRANSITION_NOT_ALLOWED);
        }
    }
}
