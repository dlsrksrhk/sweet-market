package com.sweet.market.payment.application;

public interface PaymentGateway {

    String approve(Long orderId, long amount);

    void cancel(String externalPaymentId);
}
