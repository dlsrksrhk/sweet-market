package com.sweet.market.order.admin;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.repository.SettlementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrderQueryService {

    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;

    public AdminOrderQueryService(
            OrderRepository orderRepository,
            SettlementRepository settlementRepository
    ) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryResponse> search(AdminOrderSearchRequest request, Pageable pageable) {
        return orderRepository.searchAdminOrders(
                request.buyerId(),
                request.sellerId(),
                request.status(),
                request.productId(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse findDetail(Long orderId) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        boolean settlementExists = settlementRepository.existsByOrderId(orderId);
        return AdminOrderDetailResponse.from(order, settlementExists);
    }
}
