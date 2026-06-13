package com.sweet.market.delivery.application;

import org.springframework.stereotype.Component;

@Component
public class FakeDeliveryClient implements DeliveryClient {

    @Override
    public String start(Long orderId) {
        return "fake-tracking-" + orderId;
    }

    @Override
    public void complete(String trackingNumber) {
    }
}
