package com.sweet.market.settlement.admin;

public enum AdminSettlementRetryResultCode {
    CREATED("정산이 생성되었습니다."),
    ALREADY_SETTLED("이미 정산된 주문입니다."),
    ORDER_NOT_CONFIRMED("구매 확정 상태가 아니라 정산할 수 없습니다."),
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다."),
    BATCH_FAILED("정산 배치 실행에 실패했습니다.");

    private final String message;

    AdminSettlementRetryResultCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
