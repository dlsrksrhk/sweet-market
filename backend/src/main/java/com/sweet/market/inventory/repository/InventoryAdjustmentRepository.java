package com.sweet.market.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.inventory.domain.InventoryAdjustment;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {
}
