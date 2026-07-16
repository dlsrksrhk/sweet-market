package com.sweet.market.productview.repository;

import com.sweet.market.productview.domain.ProductViewDeduplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProductViewDeduplicationRepository extends JpaRepository<ProductViewDeduplication, ProductViewDeduplication.IdValue> {

    @Modifying
    @Query(value = """
            insert into product_view_deduplications (product_id, visitor_hash, last_counted_at)
            values (:productId, :visitorHash, :viewedAt)
            on conflict (product_id, visitor_hash) do update
            set last_counted_at = excluded.last_counted_at
            where product_view_deduplications.last_counted_at < :deduplicationCutoff
            """, nativeQuery = true)
    int advanceLastCountedAt(
            @Param("productId") Long productId,
            @Param("visitorHash") String visitorHash,
            @Param("viewedAt") Instant viewedAt,
            @Param("deduplicationCutoff") Instant deduplicationCutoff
    );
}
