package com.sweet.market.refund.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.refund.domain.RefundRequest;

import jakarta.persistence.LockModeType;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<RefundRequest> findByOrderId(Long orderId);

    @Query("""
            select r
            from RefundRequest r
            where r.order.id in :orderIds
            """)
    List<RefundRequest> findByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderById(Long id);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderByOrderId(Long orderId);
}
