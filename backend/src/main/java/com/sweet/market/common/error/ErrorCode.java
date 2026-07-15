package com.sweet.market.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_REPORT_PERIOD(HttpStatus.BAD_REQUEST, "리포트 기간이 올바르지 않습니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "상품에 대한 권한이 없습니다."),
    PRODUCT_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 이미지를 찾을 수 없습니다."),
    PRODUCT_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "상품 이미지는 최소 1개 이상 필요합니다."),
    PRODUCT_IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "상품 이미지는 최대 10개까지 등록할 수 있습니다."),
    PRODUCT_IMAGE_INVALID_FILE(HttpStatus.BAD_REQUEST, "상품 이미지 파일이 올바르지 않습니다."),
    PRODUCT_IMAGE_UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "임시 업로드 상품 이미지를 찾을 수 없습니다."),
    PRODUCT_IMAGE_UPLOAD_EXPIRED(HttpStatus.BAD_REQUEST, "임시 업로드 상품 이미지가 만료되었습니다."),
    PRODUCT_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "변경할 수 없는 상품 상태입니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품만 주문할 수 있습니다."),
    INVENTORY_ADJUSTMENT_CONFLICT(HttpStatus.CONFLICT, "재고 조정 요청이 현재 재고 상태와 충돌합니다."),
    WISHLIST_PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품만 찜할 수 있습니다."),
    WISHLIST_OWN_PRODUCT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "자기 상품은 찜할 수 없습니다."),
    CART_OWN_PRODUCT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "자기 상품은 장바구니에 담을 수 없습니다."),
    CART_PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품만 장바구니에 담을 수 있습니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
    CART_CHECKOUT_EMPTY(HttpStatus.BAD_REQUEST, "주문할 장바구니 항목을 선택해주세요."),
    CART_CHECKOUT_INVALID_ITEMS(HttpStatus.BAD_REQUEST, "선택한 장바구니 항목이 올바르지 않습니다."),
    CART_CHECKOUT_NOT_ALLOWED(HttpStatus.CONFLICT, "주문할 수 없는 장바구니 항목이 포함되어 있습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "주문에 대한 권한이 없습니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 없는 주문 상태입니다."),
    ORDER_CONFIRM_NOT_ALLOWED(HttpStatus.CONFLICT, "구매 확정할 수 없는 주문 상태입니다."),
    ORDER_CONFLICT(HttpStatus.CONFLICT, "이미 다른 거래에서 처리된 주문 요청입니다."),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "리뷰를 작성할 수 없는 주문입니다."),
    REVIEW_ORDER_NOT_CONFIRMED(HttpStatus.CONFLICT, "구매 확정된 주문만 리뷰를 작성할 수 있습니다."),
    REVIEW_DUPLICATE(HttpStatus.CONFLICT, "이미 리뷰를 작성한 주문입니다."),
    REFUND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."),
    REFUND_REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "환불 요청에 대한 권한이 없습니다."),
    REFUND_REQUEST_NOT_ALLOWED(HttpStatus.CONFLICT, "환불 요청할 수 없는 주문 상태입니다."),
    DUPLICATE_REFUND_REQUEST(HttpStatus.CONFLICT, "이미 환불 요청된 주문입니다."),
    REFUND_REQUEST_HANDLE_NOT_ALLOWED(HttpStatus.CONFLICT, "처리할 수 없는 환불 요청 상태입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "결제에 대한 권한이 없습니다."),
    PAYMENT_APPROVE_NOT_ALLOWED(HttpStatus.CONFLICT, "승인할 수 없는 주문 상태입니다."),
    PAYMENT_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 없는 결제 상태입니다."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."),
    DELIVERY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "배송에 대한 권한이 없습니다."),
    DELIVERY_START_NOT_ALLOWED(HttpStatus.CONFLICT, "배송을 시작할 수 없는 주문 상태입니다."),
    DELIVERY_COMPLETE_NOT_ALLOWED(HttpStatus.CONFLICT, "배송을 완료할 수 없는 상태입니다."),
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산을 찾을 수 없습니다."),
    SETTLEMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "정산에 대한 권한이 없습니다."),
    SETTLEMENT_CREATE_NOT_ALLOWED(HttpStatus.CONFLICT, "정산할 수 없는 주문 상태입니다."),
    DUPLICATE_SETTLEMENT(HttpStatus.CONFLICT, "이미 정산된 주문입니다."),
    BATCH_LAUNCH_FAILED(HttpStatus.CONFLICT, "배치 실행에 실패했습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "상점을 찾을 수 없습니다."),
    STORE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "상점 운영 권한이 없습니다."),
    STORE_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "상점 소유자 권한이 필요합니다."),
    STORE_OWNER_MEMBERSHIP_PROTECTED(HttpStatus.CONFLICT, "상점 소유자 멤버십은 제거할 수 없습니다."),
    DUPLICATE_BUSINESS_STORE(HttpStatus.CONFLICT, "사업자 상점은 하나만 신청할 수 있습니다."),
    STORE_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상점 상태에서는 변경할 수 없습니다."),
    STORE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "사업자 상점만 처리할 수 있습니다."),
    PROMOTION_NOT_FOUND(HttpStatus.NOT_FOUND, "프로모션을 찾을 수 없습니다."),
    PROMOTION_LIFECYCLE_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 프로모션 상태에서는 처리할 수 없습니다."),
    COUPON_CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰 캠페인을 찾을 수 없습니다."),
    COUPON_LIFECYCLE_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 쿠폰 캠페인 상태에서는 처리할 수 없습니다."),
    COUPON_ISSUE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "쿠폰 발급 한도를 초과했습니다."),
    MEMBER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
    MEMBER_COUPON_ACCESS_DENIED(HttpStatus.FORBIDDEN, "쿠폰에 접근할 권한이 없습니다."),
    MEMBER_COUPON_NOT_ISSUED(HttpStatus.CONFLICT, "사용 가능한 발급 쿠폰이 아닙니다."),
    MEMBER_COUPON_EXPIRED(HttpStatus.CONFLICT, "쿠폰 유효기간이 만료되었습니다."),
    MEMBER_COUPON_TARGET_MISMATCH(HttpStatus.CONFLICT, "쿠폰 적용 대상 상품이 아닙니다."),
    MEMBER_COUPON_MINIMUM_PURCHASE_NOT_MET(HttpStatus.CONFLICT, "쿠폰 최소 구매 금액을 충족하지 않았습니다."),
    MEMBER_COUPON_PROMOTION_STACKING_NOT_ALLOWED(HttpStatus.CONFLICT, "프로모션과 중복 적용할 수 없는 쿠폰입니다."),
    MEMBER_COUPON_ALREADY_RESERVED(HttpStatus.CONFLICT, "쿠폰이 다른 주문에서 예약되어 있습니다."),
    CATALOG_CURSOR_INVALID(HttpStatus.BAD_REQUEST, "상품 목록 커서가 올바르지 않습니다."),
    CATALOG_CURSOR_STALE(HttpStatus.BAD_REQUEST, "상품 목록 커서가 만료되었습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
