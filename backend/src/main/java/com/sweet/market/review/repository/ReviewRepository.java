package com.sweet.market.review.repository;

import com.sweet.market.review.domain.Review;
import com.sweet.market.review.query.ReviewSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    long countByOrderId(Long orderId);

    @Query("""
            select r.order.id
            from Review r
            where r.order.id in :orderIds
            """)
    Set<Long> findReviewedOrderIds(@Param("orderIds") Collection<Long> orderIds);

    @EntityGraph(attributePaths = {"order", "product", "buyer"})
    Page<Review> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    @Query("""
            select new com.sweet.market.review.query.ReviewSummary(count(r), avg(r.rating))
            from Review r
            where r.product.id = :productId
            """)
    ReviewSummary summarizeByProductId(@Param("productId") Long productId);

    @Query("""
            select new com.sweet.market.review.query.ReviewSummary(count(r), avg(r.rating))
            from Review r
            where r.seller.id = :sellerId
            """)
    ReviewSummary summarizeBySellerId(@Param("sellerId") Long sellerId);
}
