package com.sweet.market.settlement.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.api.SettlementResponse;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;

    public SettlementService(
            SettlementRepository settlementRepository,
            OrderRepository orderRepository
    ) {
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public SettlementResponse create(Long sellerId, Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getProduct().isOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ACCESS_DENIED);
        }
        if (settlementRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SETTLEMENT);
        }

        Settlement settlement;
        try {
            settlement = Settlement.create(order);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.SETTLEMENT_CREATE_NOT_ALLOWED);
        }

        Settlement savedSettlement = settlementRepository.save(settlement);
        return SettlementResponse.from(savedSettlement);
    }
}
