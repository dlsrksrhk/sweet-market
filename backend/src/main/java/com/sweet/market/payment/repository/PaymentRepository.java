package com.sweet.market.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.payment.domain.Payment;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "order.product.images"})
    Optional<Payment> findWithOrderByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "order.product.images"})
    @Query("""
            select p
            from Payment p
            where p.order.id = :orderId
            """)
    Optional<Payment> findStateChangeTargetByOrderId(@Param("orderId") Long orderId);
}
