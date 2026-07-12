package com.sweet.market.inventory.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.domain.InventoryAdjustmentReason;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.store.application.StoreAccessService;

class InventoryAdjustmentCommitConflictTest {

    private final InventoryAdjustmentTransactionService transactionService =
            mock(InventoryAdjustmentTransactionService.class);
    private final InventoryService inventoryService = new InventoryService(
            mock(InventoryRepository.class),
            mock(InventoryAdjustmentRepository.class),
            mock(StoreAccessService.class),
            transactionService,
            mock(OrderRepository.class)
    );
    private final InventoryAdjustmentRequest request =
            new InventoryAdjustmentRequest(9, InventoryAdjustmentReason.RESTOCK, null);

    @Test
    void 내부_트랜잭션의_커밋_시점_스프링_낙관적_잠금_예외를_재고_충돌로_변환한다() {
        ObjectOptimisticLockingFailureException commitException =
                new ObjectOptimisticLockingFailureException(Inventory.class, 1L);
        when(transactionService.adjust(1L, 2L, 3L, request)).thenThrow(commitException);

        assertInventoryConflict();
    }

    @Test
    void 내부_트랜잭션의_커밋_시점_하이버네이트_낙관적_잠금_예외를_재고_충돌로_변환한다() {
        StaleObjectStateException commitException =
                new StaleObjectStateException(Inventory.class.getName(), 1L);
        when(transactionService.adjust(1L, 2L, 3L, request)).thenThrow(commitException);

        assertInventoryConflict();
    }

    private void assertInventoryConflict() {
        assertThatThrownBy(() -> inventoryService.adjust(1L, 2L, 3L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVENTORY_ADJUSTMENT_CONFLICT);
    }
}
