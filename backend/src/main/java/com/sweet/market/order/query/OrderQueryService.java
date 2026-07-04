package com.sweet.market.order.query;

import java.util.List;
import java.util.Set;

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
import com.sweet.market.review.repository.ReviewRepository;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;

    public OrderQueryService(OrderRepository orderRepository, ReviewRepository reviewRepository) {
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> findMine(Long buyerId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByBuyerIdOrderByIdDesc(buyerId, pageable);
        List<Long> orderIds = orders.getContent()
                .stream()
                .map(Order::getId)
                .toList();
        Set<Long> reviewedOrderIds = orderIds.isEmpty()
                ? Set.of()
                : reviewRepository.findReviewedOrderIds(orderIds);

        return orders.map(order -> OrderSummaryResponse.from(order, reviewedOrderIds.contains(order.getId())));
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
