package com.sweet.market.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.inventory.domain.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
}
