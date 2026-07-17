package com.sweet.market.operations.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InventoryOutcomeEventFactory {

    private final ObjectMapper objectMapper;

    public InventoryOutcomeEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OperationalEvent outcome(
            String action,
            long productId,
            long storeId,
            String salesPolicy,
            Integer availableQuantity,
            boolean soldOut,
            long aggregateVersion,
            Instant occurredAt
    ) {
        InventoryOutcomePayload payload = new InventoryOutcomePayload(
                action, productId, storeId, salesPolicy, availableQuantity, soldOut);
        return OperationalEvent.create(
                OperationalEventType.INVENTORY_OUTCOME,
                "product",
                productId,
                aggregateVersion,
                storeId,
                null,
                "product:" + productId,
                occurredAt,
                objectMapper.valueToTree(payload));
    }
}
