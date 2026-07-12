package com.sweet.market.inventory.application;

import org.springframework.stereotype.Service;

import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.product.domain.Product;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
    }

    public void initialize(Product product, int initialTotalQuantity, Long memberId) {
        Inventory inventory = inventoryRepository.save(Inventory.initialize(product, initialTotalQuantity));
        inventoryAdjustmentRepository.save(inventory.getInitializationAdjustment());
    }
}
