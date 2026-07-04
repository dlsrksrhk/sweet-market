package com.sweet.market.review.repository;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.review.domain.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    long countByOrderId(Long orderId);

    @Query("""
            select r.order.id
            from Review r
            where r.order.id in :orderIds
            """)
    Set<Long> findReviewedOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
