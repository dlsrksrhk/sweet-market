package com.sweet.market.order.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.domain.DeliveryStatus;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.order.domain.OrderStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OrderAutoConfirmService {

    private final ReentrantLock executionLock = new ReentrantLock();
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
        // Local single-instance guard for scheduler/manual overlap.
        executionLock.lock();
        boolean unlockOnExit = true;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        executionLock.unlock();
                    }
                });
                unlockOnExit = false;
            }

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
                } catch (DomainException exception) {
                    // Another process may have changed the order between selection and confirmation.
                }
            }

            return new OrderAutoConfirmResult(
                    confirmedCount,
                    deliveredBefore,
                    properties.thresholdDays(),
                    executedAt
            );
        } finally {
            if (unlockOnExit) {
                executionLock.unlock();
            }
        }
    }
}
