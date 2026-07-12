package com.sweet.market.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.sweet.market.common.error.ErrorResponse;
import com.sweet.market.common.error.GlobalExceptionHandler;
import com.sweet.market.inventory.domain.Inventory;

class InventoryOptimisticLockExceptionMappingTest {

    @Test
    void 커밋_시점의_재고_낙관적_잠금_충돌도_재고_충돌로_응답한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException(Inventory.class, 1L);

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockingFailureException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVENTORY_ADJUSTMENT_CONFLICT");
    }
}
