package com.sweet.market.settlement.repository;

import com.sweet.market.settlement.admin.AdminSettlementDetailResponse;
import com.sweet.market.settlement.admin.AdminSettlementSummaryResponse;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.domain.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "order.product.store", "order.product.store.ownerMember", "order.seller", "seller"})
    Optional<Settlement> findWithOrderByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "seller"})
    List<Settlement> findBySellerIdOrderByIdDesc(Long sellerId);

    @Query(
            value = """
                    select new com.sweet.market.settlement.admin.AdminSettlementSummaryResponse(
                        s.id,
                        o.id,
                        seller.id,
                        seller.nickname,
                        p.id,
                        p.title,
                        s.amount,
                        s.status,
                        s.settledAt,
                        o.memberCouponId,
                        o.couponDiscountAmount
                    )
                    from Settlement s
                    join s.order o
                    join s.seller seller
                    join o.product p
                    where (:orderId is null or o.id = :orderId)
                      and (:sellerId is null or seller.id = :sellerId)
                      and (:status is null or s.status = :status)
                      and s.settledAt >= :settledFrom
                      and s.settledAt <= :settledTo
                    """,
            countQuery = """
                    select count(s)
                    from Settlement s
                    join s.order o
                    join s.seller seller
                    where (:orderId is null or o.id = :orderId)
                      and (:sellerId is null or seller.id = :sellerId)
                      and (:status is null or s.status = :status)
                      and s.settledAt >= :settledFrom
                      and s.settledAt <= :settledTo
                    """
    )
    Page<AdminSettlementSummaryResponse> searchAdminSettlements(
            @Param("orderId") Long orderId,
            @Param("sellerId") Long sellerId,
            @Param("status") SettlementStatus status,
            @Param("settledFrom") LocalDateTime settledFrom,
            @Param("settledTo") LocalDateTime settledTo,
            Pageable pageable
    );

    @Query("""
            select new com.sweet.market.settlement.admin.AdminSettlementDetailResponse(
                s.id,
                o.id,
                o.status,
                o.confirmedAt,
                buyer.id,
                buyer.nickname,
                seller.id,
                seller.nickname,
                p.id,
                p.title,
                s.amount,
                s.status,
                s.settledAt,
                o.memberCouponId,
                o.couponDiscountAmount
            )
            from Settlement s
            join s.order o
            join o.buyer buyer
            join s.seller seller
            join o.product p
            where s.id = :settlementId
            """)
    Optional<AdminSettlementDetailResponse> findAdminSettlementDetail(@Param("settlementId") Long settlementId);

    @Modifying
    @Query(value = """
            insert into settlements (order_id, seller_id, amount, status, settled_at)
            values (:orderId, :sellerId, :amount, :status, :settledAt)
            on conflict (order_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("orderId") Long orderId,
            @Param("sellerId") Long sellerId,
            @Param("amount") long amount,
            @Param("status") String status,
            @Param("settledAt") LocalDateTime settledAt
    );

    @Query("""
            select coalesce(sum(s.amount), 0)
            from Settlement s
            where s.seller.id = :sellerId
              and s.status = com.sweet.market.settlement.domain.SettlementStatus.COMPLETED
            """)
    Long sumCompletedAmountBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            select coalesce(sum(s.amount), 0)
            from Settlement s
            where s.seller.id = :sellerId
              and s.status = com.sweet.market.settlement.domain.SettlementStatus.COMPLETED
              and s.settledAt >= :fromInclusive
              and s.settledAt < :toExclusive
            """)
    Long sumCompletedAmountBySellerIdAndSettledAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query("""
            select new com.sweet.market.seller.report.SellerRecentSettlementResponse(
                s.id,
                o.id,
                p.id,
                p.title,
                s.amount,
                s.status,
                s.settledAt
            )
            from Settlement s
            join s.order o
            join o.product p
            where s.seller.id = :sellerId
              and s.status in (
                  com.sweet.market.settlement.domain.SettlementStatus.COMPLETED,
                  com.sweet.market.settlement.domain.SettlementStatus.FAILED
              )
              and s.settledAt >= :fromInclusive
              and s.settledAt < :toExclusive
            order by s.settledAt desc, s.id desc
            """)
    List<com.sweet.market.seller.report.SellerRecentSettlementResponse> findRecentSettlementsBySellerIdAndSettledAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );
}
