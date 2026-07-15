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
import com.sweet.market.order.repository.OrderRepository;

@Service
public class CouponReservationExpiryTransactionService {

    private final CouponReservationRepository couponReservationRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public CouponReservationExpiryTransactionService(
            CouponReservationRepository couponReservationRepository,
            OrderRepository orderRepository,
            InventoryService inventoryService
    ) {
        this.couponReservationRepository = couponReservationRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireReservation(Long reservationId, Instant now) {
        Long orderId = couponReservationRepository.findOrderIdByReservationId(reservationId).orElse(null);
        if (orderId == null) {
            return;
        }

        Order order = orderRepository.findStateChangeTargetById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        CouponReservation reservation = couponReservationRepository.findActiveByOrderIdForUpdate(orderId).orElse(null);
        if (reservation == null
                || reservation.getStatus() != CouponReservationStatus.RESERVED
                || reservation.getExpiresAt().isAfter(now)) {
            return;
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            return;
        }

        order.cancel();
        inventoryService.releaseForPreShippingExit(order);
        reservation.expire(now);
    }
}
