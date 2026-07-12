package com.sweet.market.inventory.application;

import java.time.LocalDateTime;

import com.sweet.market.inventory.domain.InventoryAdjustment;
import com.sweet.market.inventory.domain.InventoryAdjustmentReason;
import com.sweet.market.inventory.domain.InventoryChangeType;

public record InventoryAdjustmentResponse(
        Long adjustmentId,
        Long productId,
        InventoryChangeType changeType,
        InventoryAdjustmentReason reason,
        String referenceNote,
        int beforeTotalQuantity,
        int afterTotalQuantity,
        int beforeReservedQuantity,
        int afterReservedQuantity,
        Long actorMemberId,
        String actorNickname,
        LocalDateTime occurredAt
) {

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        Long actorMemberId = adjustment.getActor() == null ? null : adjustment.getActor().getId();
        String actorNickname = adjustment.getActor() == null ? null : adjustment.getActor().getNickname();
        return new InventoryAdjustmentResponse(
                adjustment.getId(),
                adjustment.getProduct().getId(),
                adjustment.getChangeType(),
                adjustment.getReason(),
                adjustment.getReferenceNote(),
                adjustment.getBeforeTotalQuantity(),
                adjustment.getAfterTotalQuantity(),
                adjustment.getBeforeReservedQuantity(),
                adjustment.getAfterReservedQuantity(),
                actorMemberId,
                actorNickname,
                adjustment.getOccurredAt()
        );
    }
}
