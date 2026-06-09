package com.sweet.market.delivery.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.delivery.domain.Delivery;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "order.product.images"})
    Optional<Delivery> findWithOrderByOrderId(Long orderId);
}
