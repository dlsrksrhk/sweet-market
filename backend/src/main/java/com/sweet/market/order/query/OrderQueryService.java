package com.sweet.market.order.query;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.api.OrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.refund.domain.RefundRequest;
import com.sweet.market.refund.repository.RefundRequestRepository;
import com.sweet.market.review.repository.ReviewRepository;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final RefundRequestRepository refundRequestRepository;

    public OrderQueryService(
            OrderRepository orderRepository,
            ReviewRepository reviewRepository,
            RefundRequestRepository refundRequestRepository
    ) {
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
        this.refundRequestRepository = refundRequestRepository;
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
        Map<Long, RefundRequest> refundRequestsByOrderId = shouldLoadRefundRequests(orders.getContent())
                ? refundRequestRepository.findByOrderIdIn(orderIds)
                        .stream()
                        .collect(Collectors.toMap(refundRequest -> refundRequest.getOrder().getId(), Function.identity()))
                : Map.of();

        return orders.map(order -> OrderSummaryResponse.from(
                order,
                reviewedOrderIds.contains(order.getId()),
                refundRequestsByOrderId.get(order.getId())
        ));
    }

    @Transactional(readOnly = true)
    public OrderResponse findOne(Long buyerId, Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        RefundRequest refundRequest = refundRequestRepository.findByOrderId(orderId)
                .orElse(null);

        return OrderResponse.from(order, refundRequest);
    }

    private boolean shouldLoadRefundRequests(List<Order> orders) {
        return orders.stream()
                .map(Order::getStatus)
                .anyMatch(status -> status == OrderStatus.DELIVERED
                        || status == OrderStatus.REFUND_REQUESTED
                        || status == OrderStatus.REFUNDED);
    }
}
