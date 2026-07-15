package com.sweet.market.coupon.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.coupon.domain.CouponReservation;
import com.sweet.market.coupon.domain.CouponReservationStatus;
import com.sweet.market.coupon.repository.CouponReservationRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;

@Service
public class CouponReservationExpiryTransactionService {

    private final CouponReservationRepository couponReservationRepository;
    private final InventoryService inventoryService;

    public CouponReservationExpiryTransactionService(
            CouponReservationRepository couponReservationRepository,
            InventoryService inventoryService
    ) {
        this.couponReservationRepository = couponReservationRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireReservation(Long reservationId, Instant now) {
        CouponReservation reservation = couponReservationRepository.findByIdForUpdate(reservationId).orElse(null);
        if (reservation == null
                || reservation.getStatus() != CouponReservationStatus.RESERVED
                || reservation.getExpiresAt().isAfter(now)) {
            return;
        }

        Order order = reservation.getOrder();
        if (order.getStatus() != OrderStatus.CREATED) {
            return;
        }

        order.cancel();
        inventoryService.releaseForPreShippingExit(order);
        reservation.expire(now);
    }
}
