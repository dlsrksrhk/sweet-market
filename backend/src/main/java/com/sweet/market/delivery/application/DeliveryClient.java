package com.sweet.market.delivery.application;

public interface DeliveryClient {

    String start(Long orderId);

    void complete(String trackingNumber);
}
