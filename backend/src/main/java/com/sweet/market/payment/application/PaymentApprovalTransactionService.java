package com.sweet.market.payment.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.domain.CouponReservation;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.purchase.PurchaseOutcomeEventFactory;
import com.sweet.market.payment.api.PaymentResponse;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PaymentApprovalTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final CouponRedemptionService couponRedemptionService;
    private final MemberCouponRepository memberCouponRepository;
    private final OperationalEventRecorder operationalEventRecorder;
    private final PurchaseOutcomeEventFactory purchaseOutcomeEventFactory;

    public PaymentApprovalTransactionService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentGateway paymentGateway,
            CouponRedemptionService couponRedemptionService,
            MemberCouponRepository memberCouponRepository,
            OperationalEventRecorder operationalEventRecorder,
            PurchaseOutcomeEventFactory purchaseOutcomeEventFactory
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.couponRedemptionService = couponRedemptionService;
        this.memberCouponRepository = memberCouponRepository;
        this.operationalEventRecorder = operationalEventRecorder;
        this.purchaseOutcomeEventFactory = purchaseOutcomeEventFactory;
    }

    @Transactional
    public PaymentResponse approve(Long memberId, Long orderId) {
        Instant approvedAt = Instant.now();
        ApprovalTarget target = prepareApproval(memberId, orderId, approvedAt);
        String externalPaymentId = paymentGateway.approve(target.order().getId(), target.order().getFinalPrice());
        return finalizeApproval(target, approvedAt, externalPaymentId);
    }

    @Transactional
    public PaymentResponse approveWithoutGateway(Long memberId, Long orderId) {
        Instant approvedAt = Instant.now();
        ApprovalTarget target = prepareApproval(memberId, orderId, approvedAt);
        return finalizeApproval(target, approvedAt, "INTERNAL_ZERO_AMOUNT:" + target.order().getId());
    }

    private ApprovalTarget prepareApproval(Long memberId, Long orderId, Instant approvedAt) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        if (!order.canApprovePayment()) {
            throw new BusinessException(ErrorCode.PAYMENT_APPROVE_NOT_ALLOWED);
        }

        try {
            return new ApprovalTarget(order, couponRedemptionService.prepareForPayment(order, approvedAt));
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_APPROVE_NOT_ALLOWED, exception);
        }
    }

    private PaymentResponse finalizeApproval(ApprovalTarget target, Instant approvedAt, String externalPaymentId) {
        couponRedemptionService.consumeAfterPaymentApproval(target.reservation(), approvedAt);
        Payment payment = paymentRepository.save(Payment.approve(target.order(), externalPaymentId));
        Order order = target.order();
        Long couponCampaignId = order.getMemberCouponId() == null ? null
                : memberCouponRepository.findById(order.getMemberCouponId())
                        .map(memberCoupon -> memberCoupon.getCampaign().getId())
                        .orElse(null);
        operationalEventRecorder.record(purchaseOutcomeEventFactory.orderStatusChanged(
                "PAID", order.getId(), order.getProduct().getStore().getId(), order.getProduct().getId(),
                order.getPromotionCampaignId(), couponCampaignId,
                order.getPromotionDiscountAmount(), order.getCouponDiscountAmount(), approvedAt));
        return PaymentResponse.from(payment);
    }

    private record ApprovalTarget(Order order, CouponReservation reservation) {
    }
}
