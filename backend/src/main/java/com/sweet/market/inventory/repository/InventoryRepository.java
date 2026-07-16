package com.sweet.market.inventory.repository;

import com.sweet.market.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update inventories
            set reserved_quantity = reserved_quantity + 1,
                version = version + 1
            where product_id = :productId
              and total_quantity - reserved_quantity > 0
            """, nativeQuery = true)
    int reserveOneIfAvailable(@Param("productId") Long productId);

    Optional<Inventory> findByProductId(Long productId);

    Optional<Inventory> findByProductIdAndProductStoreId(Long productId, Long storeId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("""
            select inventory
            from Inventory inventory
            where inventory.product.id = :productId
              and inventory.product.store.id = :storeId
            """)
    Optional<Inventory> findForAdjustment(
            @Param("storeId") Long storeId,
            @Param("productId") Long productId
    );
}
