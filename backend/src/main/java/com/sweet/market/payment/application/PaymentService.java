package com.sweet.market.payment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.api.PaymentResponse;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.domain.PaymentStatus;
import com.sweet.market.payment.repository.PaymentRepository;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentGateway paymentGateway
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public PaymentResponse approve(Long memberId, Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }

        Payment payment;
        try {
            String externalPaymentId = paymentGateway.approve(order.getId(), order.getProduct().getPrice());
            payment = Payment.approve(order, externalPaymentId);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_APPROVE_NOT_ALLOWED);
        }

        Payment savedPayment = paymentRepository.save(payment);
        return PaymentResponse.from(savedPayment);
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
            if (payment.canCancel()) {
                paymentGateway.cancel(payment.getExternalPaymentId());
            }
            payment.cancel();
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
        }

        return PaymentResponse.from(payment);
    }
}
