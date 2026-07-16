package com.sweet.market.order.repository;

import com.sweet.market.order.admin.AdminOrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.seller.report.SellerDailySalesResponse;
import com.sweet.market.seller.report.SellerOrderStatusCountProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"product", "product.store", "product.store.ownerMember", "seller"})
    Page<Order> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "product", "product.store", "product.store.ownerMember", "product.images", "seller"})
    Optional<Order> findWithBuyerAndProductById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"buyer", "product", "product.store", "product.store.ownerMember", "product.images", "seller"})
    @Query("""
            select o
            from Order o
            where o.id = :id
            """)
    Optional<Order> findStateChangeTargetById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"buyer", "product", "product.store", "product.store.ownerMember", "seller"})
    Optional<Order> findReviewTargetById(Long id);

    @EntityGraph(attributePaths = {"product", "product.store", "product.store.ownerMember", "seller"})
    @Query("""
            select o
            from Order o
            where o.id = :orderId
            """)
    Optional<Order> findSettlementTargetById(@Param("orderId") Long orderId);

    @EntityGraph(attributePaths = {"buyer", "product", "product.store", "product.store.ownerMember", "seller"})
    Optional<Order> findAdminSettlementRetryTargetById(Long orderId);

    @Query(
            value = """
                    select new com.sweet.market.order.admin.AdminOrderSummaryResponse(
                        o.id,
                        p.id,
                        p.title,
                        o.finalPrice,
                        buyer.id,
                        buyer.nickname,
                        seller.id,
                        seller.nickname,
                        o.status,
                        p.status,
                        o.orderedAt,
                        o.memberCouponId,
                        o.couponDiscountAmount
                    )
                    from Order o
                    join o.buyer buyer
                    join o.product p
                    join o.seller seller
                    where (:buyerId is null or buyer.id = :buyerId)
                      and (:sellerId is null or seller.id = :sellerId)
                      and (:status is null or o.status = :status)
                      and (:productId is null or p.id = :productId)
                    """,
            countQuery = """
                    select count(o)
                    from Order o
                    join o.buyer buyer
                    join o.product p
                    join o.seller seller
                    where (:buyerId is null or buyer.id = :buyerId)
                      and (:sellerId is null or seller.id = :sellerId)
                      and (:status is null or o.status = :status)
                      and (:productId is null or p.id = :productId)
                    """
    )
    Page<AdminOrderSummaryResponse> searchAdminOrders(
            @Param("buyerId") Long buyerId,
            @Param("sellerId") Long sellerId,
            @Param("status") OrderStatus status,
            @Param("productId") Long productId,
            Pageable pageable
    );

    long countByBuyerId(Long buyerId);

    @Query("""
            select count(o)
            from Order o
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
            """)
    long countConfirmedOrdersBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            select count(o)
            from Order o
            where o.seller.id = :sellerId
              and o.orderedAt >= :fromInclusive
              and o.orderedAt < :toExclusive
            """)
    long countOrdersBySellerIdAndOrderedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query("""
            select count(o)
            from Order o
            join o.product p
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            """)
    long countConfirmedOrdersBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query("""
            select coalesce(sum(o.finalPrice), 0)
            from Order o
            join o.product p
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            """)
    Long sumConfirmedSalesAmountBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query("""
            select new com.sweet.market.seller.report.SellerDailySalesResponse(
                cast(o.confirmedAt as localdate),
                count(o),
                coalesce(sum(o.finalPrice), 0)
            )
            from Order o
            join o.product p
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            group by cast(o.confirmedAt as localdate)
            order by cast(o.confirmedAt as localdate) asc
            """)
    List<SellerDailySalesResponse> findDailyConfirmedSalesBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query("""
            select new com.sweet.market.seller.report.SellerProductRankingResponse(
                p.id,
                p.title,
                coalesce(
                    (
                        select min(representativeImage.imageUrl)
                        from ProductImage representativeImage
                        where representativeImage.product = p
                          and representativeImage.representative = true
                          and representativeImage.sortOrder = (
                              select min(firstRepresentativeImage.sortOrder)
                              from ProductImage firstRepresentativeImage
                              where firstRepresentativeImage.product = p
                                and firstRepresentativeImage.representative = true
                          )
                    ),
                    (
                        select min(orderedImage.imageUrl)
                        from ProductImage orderedImage
                        where orderedImage.product = p
                          and orderedImage.sortOrder = (
                              select min(firstImage.sortOrder)
                              from ProductImage firstImage
                              where firstImage.product = p
                          )
                    )
                ),
                count(o),
                coalesce(sum(o.finalPrice), 0),
                max(o.confirmedAt)
            )
            from Order o
            join o.product p
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            group by p.id, p.title
            order by coalesce(sum(o.finalPrice), 0) desc, count(o) desc, max(o.confirmedAt) desc, p.id desc
            """)
    List<com.sweet.market.seller.report.SellerProductRankingResponse> findTopProductRankingsBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );

    @Query("""
            select new com.sweet.market.seller.report.SellerRecentSaleResponse(
                o.id,
                p.id,
                p.title,
                buyer.nickname,
                o.finalPrice,
                o.confirmedAt,
                s.status
            )
            from Order o
            join o.buyer buyer
            join o.product p
            left join Settlement s on s.order = o
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            order by o.confirmedAt desc, o.id desc
            """)
    List<com.sweet.market.seller.report.SellerRecentSaleResponse> findRecentConfirmedSalesBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );

    @Query("""
            select o.status as status, count(o) as count
            from Order o
            where o.seller.id = :sellerId
            group by o.status
            """)
    List<SellerOrderStatusCountProjection> countOrderStatusesBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            select coalesce(sum(o.finalPrice), 0)
            from Order o
            join o.product p
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and not exists (select 1 from Settlement s where s.order = o)
            """)
    Long sumUnsettledConfirmedAmountBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            select coalesce(sum(o.finalPrice), 0)
            from Order o
            join o.product p
            where o.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
              and not exists (select 1 from Settlement s where s.order = o)
            """)
    Long sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );
}
