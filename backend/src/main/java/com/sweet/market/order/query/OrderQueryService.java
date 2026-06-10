package com.sweet.market.order.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.api.OrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> findMine(Long buyerId, Pageable pageable) {
        return orderRepository.findByBuyerIdOrderByIdDesc(buyerId, pageable)
                .map(OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse findOne(Long buyerId, Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        return OrderResponse.from(order);
    }
}
