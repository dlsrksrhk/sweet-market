package com.sweet.market.payment.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.inventory.InventoryOutcomeEventFactory;
import com.sweet.market.operations.purchase.PurchaseOutcomeEventFactory;
import com.sweet.market.payment.api.PaymentResponse;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.domain.PaymentStatus;
import com.sweet.market.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final InventoryService inventoryService;
    private final PaymentApprovalTransactionService paymentApprovalTransactionService;
    private final PaymentFailureCompensationService paymentFailureCompensationService;
    private final MemberCouponRepository memberCouponRepository;
    private final OperationalEventRecorder operationalEventRecorder;
    private final PurchaseOutcomeEventFactory purchaseOutcomeEventFactory;
    private final InventoryOutcomeEventFactory inventoryOutcomeEventFactory;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentGateway paymentGateway,
            InventoryService inventoryService,
            PaymentApprovalTransactionService paymentApprovalTransactionService,
            PaymentFailureCompensationService paymentFailureCompensationService,
            MemberCouponRepository memberCouponRepository,
            OperationalEventRecorder operationalEventRecorder,
            PurchaseOutcomeEventFactory purchaseOutcomeEventFactory,
            InventoryOutcomeEventFactory inventoryOutcomeEventFactory
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.inventoryService = inventoryService;
        this.paymentApprovalTransactionService = paymentApprovalTransactionService;
        this.paymentFailureCompensationService = paymentFailureCompensationService;
        this.memberCouponRepository = memberCouponRepository;
        this.operationalEventRecorder = operationalEventRecorder;
        this.purchaseOutcomeEventFactory = purchaseOutcomeEventFactory;
        this.inventoryOutcomeEventFactory = inventoryOutcomeEventFactory;
    }

    public PaymentResponse approve(Long memberId, Long orderId) {
        try {
            return paymentApprovalTransactionService.approve(memberId, orderId);
        } catch (PaymentGatewayException exception) {
            paymentFailureCompensationService.compensate(orderId);
            throw new BusinessException(ErrorCode.PAYMENT_APPROVE_NOT_ALLOWED, exception);
        }
    }

    @Transactional
    public PaymentResponse cancel(Long memberId, Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!order.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        Payment payment = paymentRepository.findStateChangeTargetByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        try {
            if (payment.getStatus() == PaymentStatus.APPROVED && !payment.canCancel()) {
                throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
            }
            boolean canCancel = payment.canCancel();
            if (canCancel) {
                paymentGateway.cancel(payment.getExternalPaymentId());
            }
            payment.cancel();
            if (canCancel) {
                inventoryService.releaseForPreShippingExit(order);
                orderRepository.flush();
                Instant occurredAt = Instant.now();
                recordCanceled(order, occurredAt);
                recordSingleItemRestore(order, occurredAt);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED, exception);
        }

        return PaymentResponse.from(payment);
    }

    private void recordCanceled(Order order, Instant occurredAt) {
        Long couponCampaignId = order.getMemberCouponId() == null ? null
                : memberCouponRepository.findById(order.getMemberCouponId())
                        .map(memberCoupon -> memberCoupon.getCampaign().getId())
                        .orElse(null);
        operationalEventRecorder.record(purchaseOutcomeEventFactory.orderStatusChanged(
                "CANCELED", order.getId(), order.getProduct().getStore().getId(), order.getProduct().getId(),
                order.getPromotionCampaignId(), couponCampaignId,
                order.getPromotionDiscountAmount(), order.getCouponDiscountAmount(), occurredAt));
    }

    private void recordSingleItemRestore(Order order, Instant occurredAt) {
        if (!order.getProduct().isSingleItem()) {
            return;
        }
        operationalEventRecorder.record(inventoryOutcomeEventFactory.outcome(
                "RESTORE", order.getProduct().getId(), order.getProduct().getStore().getId(),
                order.getProduct().getSalesPolicy().name(), null, false,
                order.getProduct().getVersion(), occurredAt));
    }
}
