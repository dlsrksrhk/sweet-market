package com.sweet.market.settlement.batch;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Component
public class SettlementItemProcessor implements ItemProcessor<Long, Settlement> {

    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;

    public SettlementItemProcessor(
            OrderRepository orderRepository,
            SettlementRepository settlementRepository
    ) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
    }

    @Override
    public Settlement process(Long orderId) {
        if (settlementRepository.existsByOrderId(orderId)) {
            throw new SettlementBatchSkippableException("Settlement already exists for order: " + orderId);
        }

        Order order = orderRepository.findSettlementTargetById(orderId)
                .orElseThrow(() -> new SettlementBatchSkippableException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new SettlementBatchSkippableException("Order is not confirmed: " + orderId);
        }

        try {
            return Settlement.create(order);
        } catch (IllegalStateException exception) {
            throw new SettlementBatchSkippableException("Order cannot be settled: " + orderId, exception);
        }
    }
}
