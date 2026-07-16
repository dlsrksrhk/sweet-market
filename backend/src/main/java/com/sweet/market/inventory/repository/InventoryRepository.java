package com.sweet.market.inventory.repository;

import com.sweet.market.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

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
