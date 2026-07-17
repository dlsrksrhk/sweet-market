package com.sweet.market.order.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.inventory.InventoryOutcomeEventFactory;
import com.sweet.market.operations.purchase.PurchaseOutcomeEventFactory;
import com.sweet.market.payment.application.PaymentGateway;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.domain.PaymentStatus;
import com.sweet.market.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final InventoryService inventoryService;
    private final CouponRedemptionService couponRedemptionService;
    private final MemberCouponRepository memberCouponRepository;
    private final OperationalEventRecorder operationalEventRecorder;
    private final PurchaseOutcomeEventFactory purchaseOutcomeEventFactory;
    private final InventoryOutcomeEventFactory inventoryOutcomeEventFactory;

    public OrderService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            InventoryService inventoryService,
            CouponRedemptionService couponRedemptionService,
            MemberCouponRepository memberCouponRepository,
            OperationalEventRecorder operationalEventRecorder,
            PurchaseOutcomeEventFactory purchaseOutcomeEventFactory,
            InventoryOutcomeEventFactory inventoryOutcomeEventFactory
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.inventoryService = inventoryService;
        this.couponRedemptionService = couponRedemptionService;
        this.memberCouponRepository = memberCouponRepository;
        this.operationalEventRecorder = operationalEventRecorder;
        this.purchaseOutcomeEventFactory = purchaseOutcomeEventFactory;
        this.inventoryOutcomeEventFactory = inventoryOutcomeEventFactory;
    }

    @Transactional
    public OrderResponse cancel(Long buyerId, Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.CANCELED) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            couponRedemptionService.releaseForCanceledOrder(order, Instant.now());
            order.cancel();
            inventoryService.releaseForPreShippingExit(order);
            orderRepository.flush();
            Instant occurredAt = Instant.now();
            recordStatus(order, "CANCELED", occurredAt);
            recordSingleItemInventory(order, "RESTORE", false, occurredAt);
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        Payment payment = paymentRepository.findStateChangeTargetByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        try {
            if (payment.getStatus() == PaymentStatus.APPROVED && !payment.canCancel()) {
                throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
            }
            if (payment.canCancel()) {
                paymentGateway.cancel(payment.getExternalPaymentId());
            }
            payment.cancel();
            inventoryService.releaseForPreShippingExit(order);
            orderRepository.flush();
            Instant occurredAt = Instant.now();
            recordStatus(order, "CANCELED", occurredAt);
            recordSingleItemInventory(order, "RESTORE", false, occurredAt);
        } catch (BusinessException exception) {
            throw exception;
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED, exception);
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse confirm(Long buyerId, Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        try {
            order.confirm();
            orderRepository.flush();
            Instant occurredAt = Instant.now();
            recordStatus(order, "CONFIRMED", occurredAt);
            recordSingleItemInventory(order, "SOLD_OUT", true, occurredAt);
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.ORDER_CONFIRM_NOT_ALLOWED, exception);
        }

        return OrderResponse.from(order);
    }

    private void recordStatus(Order order, String status, Instant occurredAt) {
        operationalEventRecorder.record(purchaseOutcomeEventFactory.orderStatusChanged(
                status, order.getId(), order.getProduct().getStore().getId(), order.getProduct().getId(),
                order.getPromotionCampaignId(), couponCampaignId(order),
                order.getPromotionDiscountAmount(), order.getCouponDiscountAmount(), occurredAt));
    }

    private void recordSingleItemInventory(
            Order order,
            String action,
            boolean soldOut,
            Instant occurredAt
    ) {
        if (!order.getProduct().isSingleItem()) {
            return;
        }
        operationalEventRecorder.record(inventoryOutcomeEventFactory.outcome(
                action, order.getProduct().getId(), order.getProduct().getStore().getId(),
                order.getProduct().getSalesPolicy().name(), null, soldOut,
                order.getProduct().getVersion(), occurredAt));
    }

    private Long couponCampaignId(Order order) {
        if (order.getMemberCouponId() == null) {
            return null;
        }
        return memberCouponRepository.findById(order.getMemberCouponId())
                .map(memberCoupon -> memberCoupon.getCampaign().getId())
                .orElse(null);
    }
}
