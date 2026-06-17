package com.sweet.market.seller.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
@Transactional(readOnly = true)
public class SellerReportQueryService {

    private static final int RECENT_DAYS = 30;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;

    public SellerReportQueryService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            SettlementRepository settlementRepository
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
    }

    public SellerDashboardReportResponse getDashboard(Long sellerId) {
        LocalDate recentTo = LocalDate.now();
        LocalDate recentFrom = recentTo.minusDays(RECENT_DAYS - 1L);
        LocalDateTime fromInclusive = recentFrom.atStartOfDay();
        LocalDateTime toExclusive = recentTo.plusDays(1).atStartOfDay();

        SellerReportTotalSummaryResponse total = new SellerReportTotalSummaryResponse(
                productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.ON_SALE),
                productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.SOLD_OUT),
                orderRepository.countConfirmedOrdersBySellerId(sellerId),
                zeroIfNull(settlementRepository.sumCompletedAmountBySellerId(sellerId)),
                zeroIfNull(orderRepository.sumUnsettledConfirmedAmountBySellerId(sellerId))
        );

        SellerReportRecentSummaryResponse recent = new SellerReportRecentSummaryResponse(
                orderRepository.countOrdersBySellerIdAndOrderedAtBetween(sellerId, fromInclusive, toExclusive),
                orderRepository.countConfirmedOrdersBySellerIdAndConfirmedAtBetween(sellerId, fromInclusive, toExclusive),
                zeroIfNull(settlementRepository.sumCompletedAmountBySellerIdAndSettledAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive
                )),
                zeroIfNull(orderRepository.sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive
                ))
        );

        return new SellerDashboardReportResponse(
                LocalDateTime.now(),
                new SellerReportPeriodResponse(RECENT_DAYS, recentFrom, recentTo),
                new SellerReportSummaryResponse(total, recent),
                expandProductStatusCounts(productRepository.countProductStatusesBySellerId(sellerId)),
                expandOrderStatusCounts(orderRepository.countOrderStatusesBySellerId(sellerId))
        );
    }

    private List<SellerStatusCountResponse> expandProductStatusCounts(List<SellerProductStatusCountProjection> rows) {
        Map<ProductStatus, SellerProductStatusCountProjection> rowByStatus = rows.stream()
                .collect(Collectors.toMap(SellerProductStatusCountProjection::getStatus, Function.identity()));

        return Arrays.stream(ProductStatus.values())
                .map(status -> new SellerStatusCountResponse(
                        status.name(),
                        rowByStatus.getOrDefault(status, new ProductStatusCount(status, 0L)).getCount()
                ))
                .toList();
    }

    private List<SellerStatusCountResponse> expandOrderStatusCounts(List<SellerOrderStatusCountProjection> rows) {
        Map<OrderStatus, SellerOrderStatusCountProjection> rowByStatus = rows.stream()
                .collect(Collectors.toMap(SellerOrderStatusCountProjection::getStatus, Function.identity()));

        return Arrays.stream(OrderStatus.values())
                .map(status -> new SellerStatusCountResponse(
                        status.name(),
                        rowByStatus.getOrDefault(status, new OrderStatusCount(status, 0L)).getCount()
                ))
                .toList();
    }

    private long zeroIfNull(Long value) {
        if (value == null) {
            return 0L;
        }
        return value;
    }

    private record ProductStatusCount(ProductStatus status, long count) implements SellerProductStatusCountProjection {

        @Override
        public ProductStatus getStatus() {
            return status;
        }

        @Override
        public long getCount() {
            return count;
        }
    }

    private record OrderStatusCount(OrderStatus status, long count) implements SellerOrderStatusCountProjection {

        @Override
        public OrderStatus getStatus() {
            return status;
        }

        @Override
        public long getCount() {
            return count;
        }
    }
}
