package com.sweet.market.payment.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.domain.CouponReservation;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.api.PaymentResponse;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.repository.PaymentRepository;

@Service
public class PaymentApprovalTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final CouponRedemptionService couponRedemptionService;

    public PaymentApprovalTransactionService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentGateway paymentGateway,
            CouponRedemptionService couponRedemptionService
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.couponRedemptionService = couponRedemptionService;
    }

    @Transactional
    public PaymentResponse approve(Long memberId, Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }

        CouponReservation reservation = couponRedemptionService.prepareForPayment(order, Instant.now());
        String externalPaymentId = paymentGateway.approve(order.getId(), order.getFinalPrice());
        couponRedemptionService.consumeAfterPaymentApproval(reservation, Instant.now());
        return PaymentResponse.from(paymentRepository.save(Payment.approve(order, externalPaymentId)));
    }

    @Transactional
    public PaymentResponse approveWithoutGateway(Long memberId, Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }

        CouponReservation reservation = couponRedemptionService.prepareForPayment(order, Instant.now());
        couponRedemptionService.consumeAfterPaymentApproval(reservation, Instant.now());
        return PaymentResponse.from(paymentRepository.save(
                Payment.approve(order, "INTERNAL_ZERO_AMOUNT:" + order.getId())
        ));
    }
}
