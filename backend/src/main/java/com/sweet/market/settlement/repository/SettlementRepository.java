package com.sweet.market.settlement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.settlement.domain.Settlement;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "order.product.seller", "seller"})
    Optional<Settlement> findWithOrderByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "seller"})
    List<Settlement> findBySellerIdOrderByIdDesc(Long sellerId);
}
