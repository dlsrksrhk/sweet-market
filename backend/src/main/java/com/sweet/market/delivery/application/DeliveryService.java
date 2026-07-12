package com.sweet.market.delivery.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.delivery.api.DeliveryResponse;
import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final DeliveryClient deliveryClient;
    private final InventoryService inventoryService;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            OrderRepository orderRepository,
            DeliveryClient deliveryClient,
            InventoryService inventoryService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.deliveryClient = deliveryClient;
        this.inventoryService = inventoryService;
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
            inventoryService.commitForShipment(order);
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
