package com.sweet.market.purchase.application;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.domain.InventoryAdjustment;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.inventory.InventoryOutcomeEventFactory;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ProductReservationService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final OperationalEventRecorder operationalEventRecorder;
    private final InventoryOutcomeEventFactory inventoryOutcomeEventFactory;

    public ProductReservationService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            OperationalEventRecorder operationalEventRecorder,
            InventoryOutcomeEventFactory inventoryOutcomeEventFactory
    ) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.operationalEventRecorder = operationalEventRecorder;
        this.inventoryOutcomeEventFactory = inventoryOutcomeEventFactory;
    }

    @Transactional
    public void reserve(Order order) {
        Product product = order.getProduct();
        if (product.isSingleItem()) {
            reserveSingleItem(order, product.getId());
            return;
        }
        reserveStockItem(order, product.getId());
    }

    private void reserveSingleItem(Order order, Long productId) {
        if (productRepository.reserveSingleItemIfOnSale(productId) == 0) {
            throw reservationFailure(productId);
        }
        Product product = productRepository.findWithStoreById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE));
        operationalEventRecorder.record(inventoryOutcomeEventFactory.outcome(
                "RESERVE", productId, product.getStore().getId(), product.getSalesPolicy().name(),
                null, false, product.getVersion(), Instant.now()));
    }

    private void reserveStockItem(Order order, Long productId) {
        if (inventoryRepository.reserveOneIfAvailable(productId) == 0) {
            throw reservationFailure(productId);
        }

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE));
        int afterReservedQuantity = inventory.getReservedQuantity();
        inventoryAdjustmentRepository.save(InventoryAdjustment.reservation(
                inventory,
                order,
                afterReservedQuantity - 1,
                afterReservedQuantity
        ));
        Instant occurredAt = Instant.now();
        operationalEventRecorder.record(inventoryOutcomeEventFactory.outcome(
                "RESERVE", productId, inventory.getProduct().getStore().getId(),
                inventory.getProduct().getSalesPolicy().name(), inventory.getAvailableQuantity(),
                inventory.getAvailableQuantity() == 0, inventory.getVersion(), occurredAt));
        if (inventory.getAvailableQuantity() == 0) {
            operationalEventRecorder.record(inventoryOutcomeEventFactory.outcome(
                    "SOLD_OUT", productId, inventory.getProduct().getStore().getId(),
                    inventory.getProduct().getSalesPolicy().name(), 0, true,
                    inventory.getVersion(), occurredAt));
        }
    }

    private BusinessException reservationFailure(Long productId) {
        ErrorCode errorCode = productRepository.findWithStoreById(productId)
                .filter(Product::isPurchasable)
                .isPresent()
                ? ErrorCode.PRODUCT_SOLD_OUT
                : ErrorCode.PRODUCT_UNAVAILABLE;
        return new BusinessException(errorCode);
    }
}
