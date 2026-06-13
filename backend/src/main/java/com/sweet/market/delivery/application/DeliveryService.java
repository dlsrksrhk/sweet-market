package com.sweet.market.delivery.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.delivery.api.DeliveryResponse;
import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final DeliveryClient deliveryClient;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            OrderRepository orderRepository,
            DeliveryClient deliveryClient
    ) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.deliveryClient = deliveryClient;
    }

    @Transactional
    public DeliveryResponse start(Long memberId, Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.DELIVERY_ACCESS_DENIED);
        }

        Delivery delivery;
        try {
            String trackingNumber = deliveryClient.start(order.getId());
            delivery = Delivery.start(order, trackingNumber);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.DELIVERY_START_NOT_ALLOWED);
        }

        Delivery savedDelivery = deliveryRepository.save(delivery);
        return DeliveryResponse.from(savedDelivery);
    }

    @Transactional
    public DeliveryResponse complete(Long memberId, Long orderId) {
        Delivery delivery = deliveryRepository.findWithOrderByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!delivery.getOrder().isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.DELIVERY_ACCESS_DENIED);
        }

        try {
            deliveryClient.complete(delivery.getTrackingNumber());
            delivery.complete();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.DELIVERY_COMPLETE_NOT_ALLOWED);
        }

        return DeliveryResponse.from(delivery);
    }
}
