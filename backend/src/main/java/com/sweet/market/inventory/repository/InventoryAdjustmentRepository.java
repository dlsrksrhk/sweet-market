package com.sweet.market.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.inventory.domain.InventoryAdjustment;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    @Query(
            value = """
                    select adjustment
                    from InventoryAdjustment adjustment
                    left join fetch adjustment.actor
                    where adjustment.product.id = :productId
                    order by adjustment.occurredAt desc, adjustment.id desc
                    """,
            countQuery = """
                    select count(adjustment)
                    from InventoryAdjustment adjustment
                    where adjustment.product.id = :productId
                    """
    )
    Page<InventoryAdjustment> findHistoryByProductId(
            @Param("productId") Long productId,
            Pageable pageable
    );
}
