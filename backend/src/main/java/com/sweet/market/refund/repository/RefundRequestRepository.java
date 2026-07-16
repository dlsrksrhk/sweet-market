package com.sweet.market.refund.repository;

import com.sweet.market.refund.domain.RefundRequest;
import com.sweet.market.refund.domain.RefundRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.store", "order.product.store.ownerMember", "order.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderById(Long id);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.store", "order.product.store.ownerMember", "order.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.store", "order.product.store.ownerMember", "order.seller", "buyer", "handledBy"})
    @Query(
            value = """
                    select r
                    from RefundRequest r
                    where r.buyer.id = :buyerId
                      and (:status is null or r.status = :status)
                    order by r.requestedAt desc, r.id desc
                    """,
            countQuery = """
                    select count(r)
                    from RefundRequest r
                    where r.buyer.id = :buyerId
                      and (:status is null or r.status = :status)
                    """
    )
    Page<RefundRequest> findBuyerRequests(
            @Param("buyerId") Long buyerId,
            @Param("status") RefundRequestStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.store", "order.product.store.ownerMember", "order.seller", "buyer", "handledBy"})
    @Query(
            value = """
                    select r
                    from RefundRequest r
                    join r.order o
                    where o.seller.id = :sellerId
                      and (:status is null or r.status = :status)
                    order by r.requestedAt desc, r.id desc
                    """,
            countQuery = """
                    select count(r)
                    from RefundRequest r
                    join r.order o
                    where o.seller.id = :sellerId
                      and (:status is null or r.status = :status)
                    """
    )
    Page<RefundRequest> findSellerRequests(
            @Param("sellerId") Long sellerId,
            @Param("status") RefundRequestStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.store", "order.product.store.ownerMember", "order.seller", "buyer", "handledBy"})
    @Query(
            value = """
                    select r
                    from RefundRequest r
                    where (:status is null or r.status = :status)
                    order by r.requestedAt desc, r.id desc
                    """,
            countQuery = """
                    select count(r)
                    from RefundRequest r
                    where (:status is null or r.status = :status)
                    """
    )
    Page<RefundRequest> findAdminRequests(
            @Param("status") RefundRequestStatus status,
            Pageable pageable
    );
}
