package com.sweet.market.order.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.order.admin.AdminOrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"product", "product.seller"})
    Page<Order> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller", "product.images"})
    Optional<Order> findWithBuyerAndProductById(Long id);

    @EntityGraph(attributePaths = {"product", "product.seller"})
    @Query("""
            select o
            from Order o
            where o.id = :orderId
            """)
    Optional<Order> findSettlementTargetById(@Param("orderId") Long orderId);

    @EntityGraph(attributePaths = {"buyer", "product", "product.seller"})
    Optional<Order> findAdminSettlementRetryTargetById(Long orderId);

    @Query(
            value = """
                    select new com.sweet.market.order.admin.AdminOrderSummaryResponse(
                        o.id,
                        p.id,
                        p.title,
                        p.price,
                        buyer.id,
                        buyer.nickname,
                        seller.id,
                        seller.nickname,
                        o.status,
                        p.status,
                        o.orderedAt
                    )
                    from Order o
                    join o.buyer buyer
                    join o.product p
                    join p.seller seller
                    where (:buyerId is null or buyer.id = :buyerId)
                      and (:sellerId is null or seller.id = :sellerId)
                      and (:status is null or o.status = :status)
                      and (:productId is null or p.id = :productId)
                    """,
            countQuery = """
                    select count(o)
                    from Order o
                    join o.buyer buyer
                    join o.product p
                    join p.seller seller
                    where (:buyerId is null or buyer.id = :buyerId)
                      and (:sellerId is null or seller.id = :sellerId)
                      and (:status is null or o.status = :status)
                      and (:productId is null or p.id = :productId)
                    """
    )
    Page<AdminOrderSummaryResponse> searchAdminOrders(
            @Param("buyerId") Long buyerId,
            @Param("sellerId") Long sellerId,
            @Param("status") OrderStatus status,
            @Param("productId") Long productId,
            Pageable pageable
    );

    long countByBuyerId(Long buyerId);
}
