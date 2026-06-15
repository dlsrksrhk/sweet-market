package com.sweet.market.delivery.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.domain.DeliveryStatus;
import com.sweet.market.order.domain.OrderStatus;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "order.product.images"})
    Optional<Delivery> findWithOrderByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product"})
    @Query("""
            select d
            from Delivery d
            where d.completedAt < :completedBefore
              and d.status = :deliveryStatus
              and d.order.status = :orderStatus
            order by d.id asc
            """)
    List<Delivery> findAutoConfirmCandidates(
            @Param("completedBefore") LocalDateTime completedBefore,
            @Param("deliveryStatus") DeliveryStatus deliveryStatus,
            @Param("orderStatus") OrderStatus orderStatus,
            Pageable pageable
    );
}
