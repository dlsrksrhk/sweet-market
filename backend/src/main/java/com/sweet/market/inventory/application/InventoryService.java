package com.sweet.market.inventory.application;

import org.hibernate.StaleObjectStateException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.application.StoreAccessService;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final StoreAccessService storeAccessService;
    private final InventoryAdjustmentTransactionService inventoryAdjustmentTransactionService;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            StoreAccessService storeAccessService,
            InventoryAdjustmentTransactionService inventoryAdjustmentTransactionService
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.storeAccessService = storeAccessService;
        this.inventoryAdjustmentTransactionService = inventoryAdjustmentTransactionService;
    }

    public void initialize(Product product, int initialTotalQuantity, Long memberId) {
        Inventory inventory = inventoryRepository.save(Inventory.initialize(product, initialTotalQuantity));
        inventoryAdjustmentRepository.save(inventory.getInitializationAdjustment());
    }

    public InventoryAdjustmentResponse adjust(
            Long memberId,
            Long storeId,
            Long productId,
            InventoryAdjustmentRequest request
    ) {
        try {
            return inventoryAdjustmentTransactionService.adjust(memberId, storeId, productId, request);
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException exception) {
            throw new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponse> history(
            Long memberId,
            Long storeId,
            Long productId,
            Integer page,
            Integer size
    ) {
        storeAccessService.requireOperator(memberId, storeId);
        inventoryRepository.findByProductIdAndProductStoreId(productId, storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? 20 : size;
        if (resolvedPage < 0 || resolvedSize < 1 || resolvedSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return inventoryAdjustmentRepository
                .findHistoryByProductId(productId, PageRequest.of(resolvedPage, resolvedSize))
                .map(InventoryAdjustmentResponse::from);
    }
}
