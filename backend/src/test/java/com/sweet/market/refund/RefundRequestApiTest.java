package com.sweet.market.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.refund.repository.RefundRequestRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefundRequestApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 재고형_상품은_환불되어도_출고_확정된_재고를_자동_복원하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("stock-buyer@example.com", "password123", "buyer");
        Long productId = createStockProduct("stock-seller@example.com", 5);
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        approveRefundRequest(sellerToken, refundRequestId);

        assertInventory(productId, 4, 0);
        assertThat(countInventoryAdjustments(orderId, "SHIPMENT_COMMITMENT")).isEqualTo(1);
        assertThat(countInventoryAdjustments(orderId, "RELEASE")).isZero();
    }

    @Test
    void 환불_승인_후에도_소비된_쿠폰_예약과_쿠폰_상태를_유지한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-refund-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-refund-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "쿠폰 환불 상품");
        Long couponId = issueFixedAmountCoupon("coupon-refund-buyer@example.com", 1_000L);
        Long orderId = createDeliveredCouponOrder(buyerToken, productId, couponId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        approveRefundRequest(sellerToken, refundRequestId);

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("USED");
    }

    @Test
    void 쿠폰_환불_요청은_주문_쿠폰_할인_스냅샷을_반환한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-refund-response-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-refund-response-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "쿠폰 환불 응답 상품");
        Long couponId = issueFixedAmountCoupon("coupon-refund-response-buyer@example.com", 1_000L);
        Long orderId = createDeliveredCouponOrder(buyerToken, productId, couponId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"쿠폰 할인 주문 환불 요청\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberCouponId").value(couponId))
                .andExpect(jsonPath("$.data.couponDiscountAmount").value(1_000L));
    }

    @Test
    void 구매자는_배송완료_주문에_환불을_요청할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.buyerId").isNumber())
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.sellerId").isNumber())
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.reason").value("상품 상태가 설명과 달라 환불을 요청합니다."))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.requestedAt").exists())
                .andExpect(jsonPath("$.data.handledById").doesNotExist())
                .andExpect(jsonPath("$.data.handledAt").doesNotExist())
                .andExpect(jsonPath("$.data.rejectReason").doesNotExist());
    }

    @Test
    void 프로모션_종료와_상품가격_변경_후에도_환불_주문_조회는_가격_스냅샷을_유지한다() throws Exception {
        String sellerToken = signupAndLogin("snapshot-refund-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("snapshot-refund-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "프로모션 환불 상품");
        Long promotionId = createStoreWidePromotion(productId, 500_000L);
        Long orderId = createDeliveredOrder(buyerToken, productId);

        jdbcTemplate.update("update products set price = ? where id = ?", 300_000L, productId);
        jdbcTemplate.update("update promotion_campaigns set lifecycle_status = 'ENDED' where id = ?", promotionId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listPrice").value(2_000_000L))
                .andExpect(jsonPath("$.data.promotionCampaignId").value(promotionId))
                .andExpect(jsonPath("$.data.promotionDiscountAmount").value(500_000L))
                .andExpect(jsonPath("$.data.finalPrice").value(1_500_000L))
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"));

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT (payload ->> 'promotionCampaignId')::bigint
                FROM operational_event_outbox
                WHERE event_type = 'ORDER_STATUS_CHANGED'
                  AND payload ->> 'result' = 'REFUNDED'
                  AND (payload ->> 'orderId')::bigint = ?
                """, Long.class, orderId)).isEqualTo(promotionId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT (payload ->> 'promotionDiscountAmount')::bigint
                FROM operational_event_outbox
                WHERE event_type = 'ORDER_STATUS_CHANGED'
                  AND payload ->> 'result' = 'REFUNDED'
                  AND (payload ->> 'orderId')::bigint = ?
                """, Long.class, orderId)).isEqualTo(500_000L);
    }

    @Test
    void 구매자는_다른_사람의_주문에_환불을_요청할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
    }

    @Test
    void 배송완료가_아닌_주문은_환불을_요청할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_ALLOWED"));
    }

    @Test
    void 같은_주문에는_환불을_중복_요청할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REFUND_REQUEST"));
    }

    @Test
    void 환불_요청은_인증_토큰이_필요하다() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 환불_사유는_열_글자_이상이어야_한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "짧은사유"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 공백으로_길이만_채운_환불_사유는_요청할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "        짧음        "
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_ALLOWED"));
    }

    @Test
    void 존재하지_않는_주문에는_환불을_요청할_수_없다() throws Exception {
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 판매자는_자신의_상품_환불_요청을_승인할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.handledById").isNumber())
                .andExpect(jsonPath("$.data.handledAt").exists());

        assertOrderStatus(orderId, "REFUNDED");
        assertPaymentStatus(orderId, "REFUNDED");
    }

    @Test
    void 판매자는_자신의_상품_환불_요청을_거절할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/reject", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rejectReason": "상품 설명과 다른 부분을 확인할 수 없습니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.handledById").isNumber())
                .andExpect(jsonPath("$.data.handledAt").exists())
                .andExpect(jsonPath("$.data.rejectReason").value("상품 설명과 다른 부분을 확인할 수 없습니다."));

        assertOrderStatus(orderId, "DELIVERED");
        assertPaymentStatus(orderId, "APPROVED");
    }

    @Test
    void 판매자는_다른_판매자의_환불_요청을_처리할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String otherSellerToken = signupAndLogin("other-seller@example.com", "password123", "otherSeller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherSellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));

        assertOrderStatus(orderId, "REFUND_REQUESTED");
        assertPaymentStatus(orderId, "APPROVED");
    }

    @Test
    void 판매자는_자신의_상품_환불_요청_목록만_페이지로_조회할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-list@example.com", "password123", "seller");
        String otherSellerToken = signupAndLogin("other-seller-list@example.com", "password123", "otherSeller");
        String buyerToken = signupAndLogin("buyer-list@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer-list@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long otherProductId = createProduct(otherSellerToken, "iPad Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long otherOrderId = createDeliveredOrder(otherBuyerToken, otherProductId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);
        createRefundRequest(otherBuyerToken, otherOrderId);

        mockMvc.perform(get("/api/seller/refund-requests")
                        .param("status", "REQUESTED")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(refundRequestId))
                .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
                .andExpect(jsonPath("$.data.content[0].productId").value(productId))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].buyerId").isNumber())
                .andExpect(jsonPath("$.data.content[0].buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.content[0].sellerId").isNumber())
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.content[0].reason").value("상품 상태가 설명과 달라 환불을 요청합니다."))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void 판매자는_환불_요청_상태로_목록을_필터링할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-filter@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-filter-requested@example.com", "password123", "buyerRequested");
        String approvedBuyerToken = signupAndLogin("buyer-filter-approved@example.com", "password123", "buyerApproved");
        String rejectedBuyerToken = signupAndLogin("buyer-filter-rejected@example.com", "password123", "buyerRejected");
        Long requestedProductId = createProduct(sellerToken, "Requested Product");
        Long approvedProductId = createProduct(sellerToken, "Approved Product");
        Long rejectedProductId = createProduct(sellerToken, "Rejected Product");
        Long requestedOrderId = createDeliveredOrder(buyerToken, requestedProductId);
        Long approvedOrderId = createDeliveredOrder(approvedBuyerToken, approvedProductId);
        Long rejectedOrderId = createDeliveredOrder(rejectedBuyerToken, rejectedProductId);
        Long requestedRefundRequestId = createRefundRequest(buyerToken, requestedOrderId);
        Long approvedRefundRequestId = createRefundRequest(approvedBuyerToken, approvedOrderId);
        Long rejectedRefundRequestId = createRefundRequest(rejectedBuyerToken, rejectedOrderId);

        approveRefundRequest(sellerToken, approvedRefundRequestId);
        rejectRefundRequest(sellerToken, rejectedRefundRequestId);

        mockMvc.perform(get("/api/seller/refund-requests")
                        .param("status", "REQUESTED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(requestedRefundRequestId))
                .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"));

        mockMvc.perform(get("/api/seller/refund-requests")
                        .param("status", "APPROVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(approvedRefundRequestId))
                .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"));

        mockMvc.perform(get("/api/seller/refund-requests")
                        .param("status", "REJECTED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(rejectedRefundRequestId))
                .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"));
    }

    @Test
    void 구매자는_자신의_환불_요청_목록만_페이지로_조회할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-buyer-list@example.com", "password123", "seller");
        String otherSellerToken = signupAndLogin("other-seller-buyer-list@example.com", "password123", "otherSeller");
        String buyerToken = signupAndLogin("buyer-refund-list@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer-refund-list@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long otherProductId = createProduct(otherSellerToken, "iPad Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long otherOrderId = createDeliveredOrder(otherBuyerToken, otherProductId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);
        createRefundRequest(otherBuyerToken, otherOrderId);

        mockMvc.perform(get("/api/refund-requests/me")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(refundRequestId))
                .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
                .andExpect(jsonPath("$.data.content[0].productId").value(productId))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[0].buyerId").isNumber())
                .andExpect(jsonPath("$.data.content[0].buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.content[0].sellerId").isNumber())
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.content[0].reason").value("상품 상태가 설명과 달라 환불을 요청합니다."))
                .andExpect(jsonPath("$.data.content[0].requestedAt").exists())
                .andExpect(jsonPath("$.data.content[0].handledById").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].handledAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].rejectReason").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void 구매자는_환불_요청_상태로_내역을_필터링할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller-buyer-filter@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-refund-filter@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer-refund-filter@example.com", "password123", "otherBuyer");
        Long requestedProductId = createProduct(sellerToken, "Requested Product");
        Long approvedProductId = createProduct(sellerToken, "Approved Product");
        Long rejectedProductId = createProduct(sellerToken, "Rejected Product");
        Long otherRequestedProductId = createProduct(sellerToken, "Other Requested Product");
        Long otherApprovedProductId = createProduct(sellerToken, "Other Approved Product");
        Long otherRejectedProductId = createProduct(sellerToken, "Other Rejected Product");
        Long requestedOrderId = createDeliveredOrder(buyerToken, requestedProductId);
        Long approvedOrderId = createDeliveredOrder(buyerToken, approvedProductId);
        Long rejectedOrderId = createDeliveredOrder(buyerToken, rejectedProductId);
        Long otherRequestedOrderId = createDeliveredOrder(otherBuyerToken, otherRequestedProductId);
        Long otherApprovedOrderId = createDeliveredOrder(otherBuyerToken, otherApprovedProductId);
        Long otherRejectedOrderId = createDeliveredOrder(otherBuyerToken, otherRejectedProductId);
        Long requestedRefundRequestId = createRefundRequest(buyerToken, requestedOrderId);
        Long approvedRefundRequestId = createRefundRequest(buyerToken, approvedOrderId);
        Long rejectedRefundRequestId = createRefundRequest(buyerToken, rejectedOrderId);
        createRefundRequest(otherBuyerToken, otherRequestedOrderId);
        Long otherApprovedRefundRequestId = createRefundRequest(otherBuyerToken, otherApprovedOrderId);
        Long otherRejectedRefundRequestId = createRefundRequest(otherBuyerToken, otherRejectedOrderId);

        approveRefundRequest(sellerToken, approvedRefundRequestId);
        rejectRefundRequest(sellerToken, rejectedRefundRequestId);
        approveRefundRequest(sellerToken, otherApprovedRefundRequestId);
        rejectRefundRequest(sellerToken, otherRejectedRefundRequestId);

        mockMvc.perform(get("/api/refund-requests/me")
                        .param("status", "REQUESTED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(requestedRefundRequestId))
                .andExpect(jsonPath("$.data.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.content[0].handledById").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].handledAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].rejectReason").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        mockMvc.perform(get("/api/refund-requests/me")
                        .param("status", "APPROVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(approvedRefundRequestId))
                .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.content[0].handledById").isNumber())
                .andExpect(jsonPath("$.data.content[0].handledAt").exists())
                .andExpect(jsonPath("$.data.content[0].rejectReason").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        mockMvc.perform(get("/api/refund-requests/me")
                        .param("status", "REJECTED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(rejectedRefundRequestId))
                .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.content[0].handledById").isNumber())
                .andExpect(jsonPath("$.data.content[0].handledAt").exists())
                .andExpect(jsonPath("$.data.content[0].rejectReason").value("상품 설명과 다른 부분을 확인할 수 없습니다."))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void 구매자는_상태_없이_전체_환불_내역을_최신순으로_페이지_조회한다() throws Exception {
        String sellerToken = signupAndLogin("seller-buyer-page-order@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer-refund-page-order@example.com", "password123", "buyer");
        Long firstProductId = createProduct(sellerToken, "First Product");
        Long secondProductId = createProduct(sellerToken, "Second Product");
        Long thirdProductId = createProduct(sellerToken, "Third Product");
        Long firstOrderId = createDeliveredOrder(buyerToken, firstProductId);
        Long secondOrderId = createDeliveredOrder(buyerToken, secondProductId);
        Long thirdOrderId = createDeliveredOrder(buyerToken, thirdProductId);
        Long firstRefundRequestId = createRefundRequest(buyerToken, firstOrderId);
        Long secondRefundRequestId = createRefundRequest(buyerToken, secondOrderId);
        Long thirdRefundRequestId = createRefundRequest(buyerToken, thirdOrderId);

        approveRefundRequest(sellerToken, secondRefundRequestId);
        rejectRefundRequest(sellerToken, thirdRefundRequestId);

        mockMvc.perform(get("/api/refund-requests/me")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(thirdRefundRequestId))
                .andExpect(jsonPath("$.data.content[1].id").value(secondRefundRequestId))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(2));

        mockMvc.perform(get("/api/refund-requests/me")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(firstRefundRequestId))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.size").value(2));
    }

    @Test
    void 환불_내역_조회는_인증_토큰이_필요하다() throws Exception {
        mockMvc.perform(get("/api/refund-requests/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 관리자는_모든_환불_요청을_승인할_수_있다() throws Exception {
        String adminToken = createAdminToken("admin@example.com");
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/admin/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.handledById").isNumber())
                .andExpect(jsonPath("$.data.handledAt").exists());

        assertOrderStatus(orderId, "REFUNDED");
        assertPaymentStatus(orderId, "REFUNDED");
    }

    @Test
    void 관리자는_모든_환불_요청을_거절할_수_있다() throws Exception {
        String adminToken = createAdminToken("admin@example.com");
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/admin/refund-requests/{refundRequestId}/reject", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rejectReason": "상품 설명과 다른 부분을 확인할 수 없습니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.handledById").isNumber())
                .andExpect(jsonPath("$.data.handledAt").exists())
                .andExpect(jsonPath("$.data.rejectReason").value("상품 설명과 다른 부분을 확인할 수 없습니다."));

        assertOrderStatus(orderId, "DELIVERED");
        assertPaymentStatus(orderId, "APPROVED");
    }

    @Test
    void 관리자는_모든_환불_요청_목록을_페이지로_조회할_수_있다() throws Exception {
        String adminToken = createAdminToken("admin-list@example.com");
        String sellerToken = signupAndLogin("seller-admin-list@example.com", "password123", "seller");
        String otherSellerToken = signupAndLogin("other-seller-admin-list@example.com", "password123", "otherSeller");
        String buyerToken = signupAndLogin("buyer-admin-list@example.com", "password123", "buyer");
        String otherBuyerToken = signupAndLogin("other-buyer-admin-list@example.com", "password123", "otherBuyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long otherProductId = createProduct(otherSellerToken, "iPad Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long otherOrderId = createDeliveredOrder(otherBuyerToken, otherProductId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);
        Long otherRefundRequestId = createRefundRequest(otherBuyerToken, otherOrderId);

        mockMvc.perform(get("/api/admin/refund-requests")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(otherRefundRequestId))
                .andExpect(jsonPath("$.data.content[0].orderId").value(otherOrderId))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("iPad Pro"))
                .andExpect(jsonPath("$.data.content[0].buyerNickname").value("otherBuyer"))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("otherSeller"))
                .andExpect(jsonPath("$.data.content[1].id").value(refundRequestId))
                .andExpect(jsonPath("$.data.content[1].orderId").value(orderId))
                .andExpect(jsonPath("$.data.content[1].productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[1].buyerNickname").value("buyer"))
                .andExpect(jsonPath("$.data.content[1].sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void 관리자는_상태_없이_전체_환불_요청을_최신순으로_페이지_조회한다() throws Exception {
        String adminToken = createAdminToken("admin-page-order@example.com");
        String sellerToken = signupAndLogin("seller-page-order@example.com", "password123", "seller");
        String firstBuyerToken = signupAndLogin("buyer-page-order-1@example.com", "password123", "firstBuyer");
        String secondBuyerToken = signupAndLogin("buyer-page-order-2@example.com", "password123", "secondBuyer");
        String thirdBuyerToken = signupAndLogin("buyer-page-order-3@example.com", "password123", "thirdBuyer");
        Long firstProductId = createProduct(sellerToken, "First Product");
        Long secondProductId = createProduct(sellerToken, "Second Product");
        Long thirdProductId = createProduct(sellerToken, "Third Product");
        Long firstOrderId = createDeliveredOrder(firstBuyerToken, firstProductId);
        Long secondOrderId = createDeliveredOrder(secondBuyerToken, secondProductId);
        Long thirdOrderId = createDeliveredOrder(thirdBuyerToken, thirdProductId);
        Long firstRefundRequestId = createRefundRequest(firstBuyerToken, firstOrderId);
        Long secondRefundRequestId = createRefundRequest(secondBuyerToken, secondOrderId);
        Long thirdRefundRequestId = createRefundRequest(thirdBuyerToken, thirdOrderId);

        approveRefundRequest(sellerToken, secondRefundRequestId);
        rejectRefundRequest(sellerToken, thirdRefundRequestId);

        mockMvc.perform(get("/api/admin/refund-requests")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(thirdRefundRequestId))
                .andExpect(jsonPath("$.data.content[1].id").value(secondRefundRequestId))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(2));

        mockMvc.perform(get("/api/admin/refund-requests")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(firstRefundRequestId))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.size").value(2));
    }

    @Test
    void 일반_회원은_관리자_환불_엔드포인트를_호출할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/admin/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 일반_회원은_관리자_환불_목록을_조회할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");

        mockMvc.perform(get("/api/admin/refund-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 이미_처리된_환불_요청은_다시_처리할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/reject", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rejectReason": "상품 설명과 다른 부분을 확인할 수 없습니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_HANDLE_NOT_ALLOWED"));
    }

    @Test
    void 존재하지_않는_환불_요청은_처리할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_FOUND"));
    }

    @Test
    void 결제가_없으면_환불_승인할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);
        jdbcTemplate.update("delete from payments where order_id = ?", orderId);

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

        assertOrderStatus(orderId, "REFUND_REQUESTED");
        assertRefundRequestStatus(refundRequestId, "REQUESTED");
    }

    @Test
    void 환불_거절_사유는_다섯_글자_이상이어야_한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        Long refundRequestId = createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/reject", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rejectReason": "짧음"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 주문_상태_변경_조회는_비관적_쓰기_잠금을_사용한다() throws Exception {
        Method method = OrderRepository.class.getMethod("findStateChangeTargetById", Long.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void 환불_요청_처리_조회는_비관적_쓰기_잠금을_사용한다() throws Exception {
        Method method = RefundRequestRepository.class.getMethod("findWithOrderById", Long.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private Long createRefundRequest(String accessToken, Long orderId) throws Exception {
        String response = mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private void approveRefundRequest(String accessToken, Long refundRequestId) throws Exception {
        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private void rejectRefundRequest(String accessToken, Long refundRequestId) throws Exception {
        mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/reject", refundRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rejectReason": "상품 설명과 다른 부분을 확인할 수 없습니다."
                                }
                                """))
                .andExpect(status().isOk());
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        SignupRequest signupRequest = new SignupRequest(email, password, nickname);
        LoginRequest loginRequest = new LoginRequest(email, password);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String createAdminToken(String email) throws Exception {
        signupAndLogin(email, "password123", "admin");
        jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", email);
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private Long createProduct(String accessToken, String title) throws Exception {
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, title.replace(" ", "-").toLowerCase() + ".jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "%s",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(storeId, title, uploadId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createStoreWidePromotion(Long productId, long discountAmount) {
        Long storeId = jdbcTemplate.queryForObject("select store_id from products where id = ?", Long.class, productId);
        jdbcTemplate.update("update stores set type = 'BUSINESS' where id = ?", storeId);
        return jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', ?, 10, '환불 스냅샷 할인',
                    current_timestamp - interval '1 minute', current_timestamp + interval '1 minute', 'DRAFT',
                    current_timestamp, current_timestamp)
                returning id
                """, Long.class, storeId, discountAmount);
    }

    private Long createStockProduct(String sellerEmail, int totalQuantity) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member seller = memberRepository.findByEmail(sellerEmail).orElseThrow();
            Store store = Store.applyBusiness(seller, "재고 상점", "소개", "법인", "123-45-67890");
            store.approve();
            storeRepository.save(store);
            Product product = Product.create(
                    store,
                    "재고 상품",
                    "설명",
                    10_000L,
                    ProductSalesPolicy.STOCK_MANAGED,
                    2,
                    totalQuantity
            );
            product.addLegacyImage("stock.jpg");
            Product savedProduct = productRepository.save(product);
            inventoryService.initialize(savedProduct, totalQuantity, seller.getId());
            return savedProduct.getId();
        });
    }

    private void assertInventory(Long productId, int totalQuantity, int reservedQuantity) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_quantity FROM inventories WHERE product_id = ?",
                Integer.class,
                productId
        )).isEqualTo(totalQuantity);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventories WHERE product_id = ?",
                Integer.class,
                productId
        )).isEqualTo(reservedQuantity);
    }

    private long countInventoryAdjustments(Long orderId, String changeType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_adjustments WHERE order_id = ? AND change_type = ?",
                Long.class,
                orderId,
                changeType
        );
        return count == null ? 0 : count;
    }

    private Long uploadImage(String accessToken, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        String response = mockMvc.perform(multipart("/api/product-image-uploads")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void assertOrderStatus(Long orderId, String status) {
        String actual = jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
        assertThat(actual).isEqualTo(status);
    }

    private void assertPaymentStatus(Long orderId, String status) {
        String actual = jdbcTemplate.queryForObject(
                "select status from payments where order_id = ?",
                String.class,
                orderId
        );
        assertThat(actual).isEqualTo(status);
    }

    private void assertRefundRequestStatus(Long refundRequestId, String status) {
        String actual = jdbcTemplate.queryForObject(
                "select status from refund_requests where id = ?",
                String.class,
                refundRequestId
        );
        assertThat(actual).isEqualTo(status);
    }

    private Long createOrder(String accessToken, Long productId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createCouponOrder(String accessToken, Long productId, Long couponId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(productId, couponId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createDeliveredOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createOrder(accessToken, productId);

        completeDelivery(accessToken, orderId);

        return orderId;
    }

    private Long createDeliveredCouponOrder(String accessToken, Long productId, Long couponId) throws Exception {
        Long orderId = createCouponOrder(accessToken, productId, couponId);

        completeDelivery(accessToken, orderId);

        return orderId;
    }

    private void completeDelivery(String accessToken, Long orderId) throws Exception {

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/start", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/complete", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private Long issueFixedAmountCoupon(String buyerEmail, long discountAmount) {
        Long campaignId = jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (
                    version, owner_type, scope, discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at,
                    validity_type, validity_days, lifecycle_status, issued_count, created_at, updated_at
                ) values (
                    0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', ?, null,
                    0, true, '환불 결제 쿠폰', current_timestamp - interval '1 day', current_timestamp + interval '1 day',
                    'DAYS_FROM_ISSUANCE', 7, 'ENDED', 0, current_timestamp, current_timestamp
                ) returning id
                """, Long.class, discountAmount);
        Long buyerId = jdbcTemplate.queryForObject("select id from members where email = ?", Long.class, buyerEmail);
        return jdbcTemplate.queryForObject("""
                insert into member_coupons (
                    member_id, coupon_campaign_id, issued_at, valid_until, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, scope, stackable, status
                ) values (
                    ?, ?, current_timestamp, current_timestamp + interval '7 days', 'FIXED_AMOUNT', ?,
                    null, 0, 'ALL_PRODUCTS', true, 'ISSUED'
                ) returning id
                """, Long.class, buyerId, campaignId, discountAmount);
    }
}
