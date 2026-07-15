package com.sweet.market.coupon.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.coupon.domain.CouponReservation;

import jakarta.persistence.LockModeType;

public interface CouponReservationRepository extends JpaRepository<CouponReservation, Long> {

    @Query("""
            select case when count(reservation) > 0 then true else false end
            from CouponReservation reservation
            where reservation.memberCoupon.id = :memberCouponId
              and reservation.status = com.sweet.market.coupon.domain.CouponReservationStatus.RESERVED
            """)
    boolean existsActiveByMemberCouponId(@Param("memberCouponId") Long memberCouponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation from CouponReservation reservation
            join fetch reservation.memberCoupon
            join fetch reservation.order
            where reservation.order.id = :orderId
              and reservation.status = com.sweet.market.coupon.domain.CouponReservationStatus.RESERVED
            """)
    Optional<CouponReservation> findActiveByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
            select reservation.id from CouponReservation reservation
            where reservation.status = com.sweet.market.coupon.domain.CouponReservationStatus.RESERVED
              and reservation.expiresAt <= :now
            """)
    List<Long> findExpiredReservationIds(@Param("now") Instant now);

    @Query("""
            select reservation.order.id from CouponReservation reservation
            where reservation.id = :reservationId
            """)
    Optional<Long> findOrderIdByReservationId(@Param("reservationId") Long reservationId);
}
