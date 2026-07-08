package com.sweet.market.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.refund.repository.RefundRequestRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.LockModeType;

class RefundRequestApiTest extends IntegrationTestSupport {

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
                .andExpect(jsonPath("$.data.reason").value("상품 상태가 설명과 달라 환불을 요청합니다."))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.requestedAt").exists())
                .andExpect(jsonPath("$.data.handledById").doesNotExist())
                .andExpect(jsonPath("$.data.handledAt").doesNotExist())
                .andExpect(jsonPath("$.data.rejectReason").doesNotExist());
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
                .andExpect(jsonPath("$.data.content[1].id").value(refundRequestId))
                .andExpect(jsonPath("$.data.content[1].orderId").value(orderId))
                .andExpect(jsonPath("$.data.content[1].productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.content[1].buyerNickname").value("buyer"))
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
        Long uploadId = uploadImage(accessToken, title.replace(" ", "-").toLowerCase() + ".jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(title, uploadId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("id").asLong();
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

    private Long createDeliveredOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createOrder(accessToken, productId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/start", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/complete", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        return orderId;
    }
}
