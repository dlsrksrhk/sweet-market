package com.sweet.market.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "order.product.images"})
    Optional<Payment> findWithOrderByOrderId(Long orderId);
}
