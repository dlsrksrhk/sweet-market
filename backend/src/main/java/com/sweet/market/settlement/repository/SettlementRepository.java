package com.sweet.market.settlement.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.settlement.domain.Settlement;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "order.product.seller", "seller"})
    Optional<Settlement> findWithOrderByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "seller"})
    List<Settlement> findBySellerIdOrderByIdDesc(Long sellerId);

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
}
