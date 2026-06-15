package com.sweet.market.order.application;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.domain.DeliveryStatus;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.order.domain.OrderStatus;

@Service
public class OrderAutoConfirmService {

    private final DeliveryRepository deliveryRepository;
    private final OrderAutoConfirmProperties properties;

    public OrderAutoConfirmService(
            DeliveryRepository deliveryRepository,
            OrderAutoConfirmProperties properties
    ) {
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
    }

    @Transactional
    public OrderAutoConfirmResult confirmDeliveredOrders() {
        return confirmDeliveredOrders(LocalDateTime.now());
    }

    @Transactional
    public OrderAutoConfirmResult confirmDeliveredOrders(LocalDateTime executedAt) {
        LocalDateTime deliveredBefore = executedAt.minusDays(properties.thresholdDays());
        int confirmedCount = 0;

        for (Delivery delivery : deliveryRepository.findAutoConfirmCandidates(
                deliveredBefore,
                DeliveryStatus.DELIVERED,
                OrderStatus.DELIVERED,
                PageRequest.of(0, properties.limit())
        )) {
            try {
                delivery.getOrder().confirm();
                confirmedCount++;
            } catch (IllegalStateException exception) {
                // Another process may have changed the order between selection and confirmation.
            }
        }

        return new OrderAutoConfirmResult(
                confirmedCount,
                deliveredBefore,
                properties.thresholdDays(),
                executedAt
        );
    }
}
