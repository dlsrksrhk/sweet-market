package com.sweet.market.order.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"product", "product.seller"})
    Page<Order> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller", "product.images"})
    Optional<Order> findWithBuyerAndProductById(Long id);
}
