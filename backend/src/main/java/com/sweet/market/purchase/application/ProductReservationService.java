package com.sweet.market.purchase.application;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.domain.InventoryAdjustment;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductReservationService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;

    public ProductReservationService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository
    ) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
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
            throw reservationFailure(order.getProduct());
        }
    }

    private void reserveStockItem(Order order, Long productId) {
        if (inventoryRepository.reserveOneIfAvailable(productId) == 0) {
            throw reservationFailure(order.getProduct());
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
    }

    private BusinessException reservationFailure(Product product) {
        ErrorCode errorCode = product.isPurchasable()
                ? ErrorCode.PRODUCT_SOLD_OUT
                : ErrorCode.PRODUCT_UNAVAILABLE;
        return new BusinessException(errorCode);
    }
}
