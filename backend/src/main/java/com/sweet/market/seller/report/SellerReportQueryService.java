package com.sweet.market.seller.report;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.settlement.repository.SettlementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Service
@Transactional(readOnly = true)
public class SellerReportQueryService {

    private static final int RECENT_DAYS = 30;
    private static final int MAX_PERIOD_DAYS = 180;
    private static final int PRODUCT_RANKING_LIMIT = 5;
    private static final int RECENT_ROW_LIMIT = 10;

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

    public SellerPeriodReportResponse getPeriodReport(Long sellerId, String fromValue, String toValue) {
        SellerPeriodRange period = resolvePeriod(fromValue, toValue);
        LocalDateTime fromInclusive = period.fromInclusive();
        LocalDateTime toExclusive = period.toExclusive();
        long orderedCount = orderRepository.countOrdersBySellerIdAndOrderedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        );
        long confirmedOrderCount = orderRepository.countConfirmedOrdersBySellerIdAndConfirmedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        );
        long confirmedSalesAmount = zeroIfNull(orderRepository.sumConfirmedSalesAmountBySellerIdAndConfirmedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        ));
        long completedSettlementAmount = zeroIfNull(settlementRepository.sumCompletedAmountBySellerIdAndSettledAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        ));
        long unsettledConfirmedAmount = zeroIfNull(orderRepository.sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        ));
        long averageConfirmedOrderAmount = confirmedOrderCount == 0 ? 0 : confirmedSalesAmount / confirmedOrderCount;
        List<SellerProductRankingResponse> productRankings =
                orderRepository.findTopProductRankingsBySellerIdAndConfirmedAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, PRODUCT_RANKING_LIMIT)
                );
        List<SellerRecentSaleResponse> recentSales =
                orderRepository.findRecentConfirmedSalesBySellerIdAndConfirmedAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, RECENT_ROW_LIMIT)
                );
        List<SellerRecentSettlementResponse> recentSettlements =
                settlementRepository.findRecentSettlementsBySellerIdAndSettledAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, RECENT_ROW_LIMIT)
                );

        return new SellerPeriodReportResponse(
                LocalDateTime.now(),
                new SellerPeriodResponse(period.from(), period.to(), period.days()),
                new SellerPeriodSummaryResponse(
                        orderedCount,
                        confirmedOrderCount,
                        confirmedSalesAmount,
                        completedSettlementAmount,
                        unsettledConfirmedAmount,
                        averageConfirmedOrderAmount
                ),
                productRankings,
                zeroFilledDailySales(
                        period,
                        orderRepository.findDailyConfirmedSalesBySellerIdAndConfirmedAtBetween(
                                sellerId,
                                fromInclusive,
                                toExclusive
                        )
                ),
                recentSales,
                recentSettlements
        );
    }

    private SellerPeriodRange resolvePeriod(String fromValue, String toValue) {
        if (fromValue == null && toValue == null) {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(RECENT_DAYS - 1L);
            return validatePeriod(from, to);
        }

        if (fromValue == null || toValue == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        try {
            return validatePeriod(LocalDate.parse(fromValue), LocalDate.parse(toValue));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }
    }

    private SellerPeriodRange validatePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1L;
        if (days > MAX_PERIOD_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        return new SellerPeriodRange(from, to, days);
    }

    private List<SellerDailySalesResponse> zeroFilledDailySales(
            SellerPeriodRange period,
            List<SellerDailySalesResponse> actualSales
    ) {
        Map<LocalDate, SellerDailySalesResponse> actualSalesByDate = actualSales.stream()
                .collect(Collectors.toMap(SellerDailySalesResponse::date, Function.identity()));

        return LongStream.range(0, period.days())
                .mapToObj(offset -> {
                    LocalDate date = period.from().plusDays(offset);
                    return actualSalesByDate.getOrDefault(date, new SellerDailySalesResponse(date, 0L, 0L));
                })
                .toList();
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

    private record SellerPeriodRange(LocalDate from, LocalDate to, long days) {

        LocalDateTime fromInclusive() {
            return from.atStartOfDay();
        }

        LocalDateTime toExclusive() {
            return to.plusDays(1).atStartOfDay();
        }
    }
}
