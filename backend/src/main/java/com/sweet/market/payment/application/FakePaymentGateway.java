package com.sweet.market.payment.application;

import org.springframework.stereotype.Component;

@Component
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public String approve(Long orderId, long amount) {
        return "fake-payment-" + orderId;
    }

    @Override
    public void cancel(String externalPaymentId) {
    }
}
