package com.sweet.market.settlement.admin;

public record AdminSettlementRetryResponse(
        AdminSettlementRetryResultCode resultCode,
        Long orderId,
        Long settlementId,
        Long jobExecutionId,
        String message
) {

    public static AdminSettlementRetryResponse of(
            AdminSettlementRetryResultCode resultCode,
            Long orderId,
            Long settlementId,
            Long jobExecutionId
    ) {
        return new AdminSettlementRetryResponse(
                resultCode,
                orderId,
                settlementId,
                jobExecutionId,
                resultCode.message()
        );
    }
}
