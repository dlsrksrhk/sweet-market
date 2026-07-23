package com.sweet.market.payment.application;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.repository.CouponReservationRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.event.OperationalFailureRecorder;
import com.sweet.market.operations.purchase.PurchaseOutcomeEventFactory;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Service
public class PaymentFailureCompensationService {

    private final OrderRepository orderRepository;
    private final CouponReservationRepository couponReservationRepository;
    private final InventoryService inventoryService;
    private final OperationalFailureRecorder operationalFailureRecorder;
    private final OperationalEventRecorder operationalEventRecorder;
    private final PurchaseOutcomeEventFactory purchaseOutcomeEventFactory;

    public PaymentFailureCompensationService(
            OrderRepository orderRepository,
            CouponReservationRepository couponReservationRepository,
            InventoryService inventoryService,
            OperationalFailureRecorder operationalFailureRecorder,
            OperationalEventRecorder operationalEventRecorder,
            PurchaseOutcomeEventFactory purchaseOutcomeEventFactory
    ) {
        this.orderRepository = orderRepository;
        this.couponReservationRepository = couponReservationRepository;
        this.inventoryService = inventoryService;
        this.operationalFailureRecorder = operationalFailureRecorder;
        this.operationalEventRecorder = operationalEventRecorder;
        this.purchaseOutcomeEventFactory = purchaseOutcomeEventFactory;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.canApprovePayment()) {
            return;
        }

        Instant occurredAt = Instant.now();
        Long couponCampaignId = couponReservationRepository.findActiveByOrderIdForUpdate(orderId)
                .map(reservation -> {
                    Long campaignId = reservation.getMemberCoupon().getCampaign().getId();
                    reservation.release(occurredAt);
                    return campaignId;
                })
                .orElse(null);
        inventoryService.releaseForFailedPaymentApproval(order);
        OperationalEvent paymentFailed = purchaseOutcomeEventFactory.paymentFailed(
                order.getId(), order.getProduct().getStore().getId(), order.getProduct().getId(),
                order.getPromotionCampaignId(), couponCampaignId,
                order.getPromotionDiscountAmount(), order.getCouponDiscountAmount(), occurredAt);
        OperationalEvent canceled = purchaseOutcomeEventFactory.orderStatusChanged(
                "CANCELED", order.getId(), order.getProduct().getStore().getId(), order.getProduct().getId(),
                order.getPromotionCampaignId(), couponCampaignId,
                order.getPromotionDiscountAmount(), order.getCouponDiscountAmount(), occurredAt);
        operationalEventRecorder.record(canceled);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                operationalFailureRecorder.recordSafely(paymentFailed);
            }
        });
    }
}
