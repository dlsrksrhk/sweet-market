package com.sweet.market.refund.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.refund.domain.RefundRequest;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderById(Long id);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderByOrderId(Long orderId);
}
