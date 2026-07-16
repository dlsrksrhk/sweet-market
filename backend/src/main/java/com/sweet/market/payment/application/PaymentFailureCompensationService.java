package com.sweet.market.payment.application;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.repository.CouponReservationRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PaymentFailureCompensationService {

    private final OrderRepository orderRepository;
    private final CouponReservationRepository couponReservationRepository;
    private final InventoryService inventoryService;

    public PaymentFailureCompensationService(
            OrderRepository orderRepository,
            CouponReservationRepository couponReservationRepository,
            InventoryService inventoryService
    ) {
        this.orderRepository = orderRepository;
        this.couponReservationRepository = couponReservationRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.canApprovePayment()) {
            return;
        }

        couponReservationRepository.findActiveByOrderIdForUpdate(orderId)
                .ifPresent(reservation -> reservation.release(Instant.now()));
        inventoryService.releaseForFailedPaymentApproval(order);
    }
}
