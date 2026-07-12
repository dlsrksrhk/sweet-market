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
import com.sweet.market.inventory.domain.InventoryAdjustment;
import com.sweet.market.inventory.domain.InventoryChangeType;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.order.domain.Order;
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

    @Transactional(readOnly = true)
    public boolean isAvailableForOrder(Product product) {
        if (product.isSingleItem()) {
            return product.isPurchasable();
        }
        return findInventory(product).getAvailableQuantity() > 0;
    }

    @Transactional
    public void reserveForOrder(Order order) {
        if (order.getProduct().isSingleItem()) {
            return;
        }
        if (hasAdjustment(order, InventoryChangeType.RESERVATION)) {
            return;
        }

        InventoryAdjustment adjustment;
        try {
            adjustment = findInventory(order.getProduct()).reserve(order);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        inventoryAdjustmentRepository.save(adjustment);
    }

    @Transactional
    public void releaseForPreShippingExit(Order order) {
        if (order.getProduct().isSingleItem()
                || hasAdjustment(order, InventoryChangeType.RELEASE)) {
            return;
        }
        inventoryAdjustmentRepository.save(findInventory(order.getProduct()).release(order));
    }

    @Transactional
    public void commitForShipment(Order order) {
        if (order.getProduct().isSingleItem()
                || hasAdjustment(order, InventoryChangeType.SHIPMENT_COMMITMENT)) {
            return;
        }
        inventoryAdjustmentRepository.save(findInventory(order.getProduct()).commitShipment(order));
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

    private Inventory findInventory(Product product) {
        return inventoryRepository.findByProductIdAndProductStoreId(product.getId(), product.getStore().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE));
    }

    private boolean hasAdjustment(Order order, InventoryChangeType changeType) {
        return inventoryAdjustmentRepository.existsByOrderIdAndChangeType(order.getId(), changeType);
    }
}
