package com.sweet.market.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.application.PaymentFailureCompensationService;
import com.sweet.market.payment.application.PaymentGateway;
import com.sweet.market.payment.application.PaymentGatewayException;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @MockitoSpyBean
    private InventoryService inventoryService;

    @Autowired
    private PaymentFailureCompensationService paymentFailureCompensationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    @Test
    void 재고형_주문은_예약되고_배송_시_재고를_판매확정한다() throws Exception {
        signupAndLogin("stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("stock-buyer@example.com", "password123", "buyer");
        Long productId = createStockProduct("stock-seller@example.com", 5);

        Long orderId = createOrder(buyerToken, productId);
        assertInventory(productId, 5, 1);
        approvePayment(buyerToken, orderId);

        startDelivery(buyerToken, orderId);

        assertInventory(productId, 4, 0);
        assertThat(countInventoryAdjustments(orderId, "SHIPMENT_COMMITMENT")).isEqualTo(1);
    }

    @Test
    void 재고형_결제_취소는_예약을_한번만_해제한다() throws Exception {
        signupAndLogin("stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("stock-buyer@example.com", "password123", "buyer");
        Long productId = createStockProduct("stock-seller@example.com", 5);
        Long orderId = createOrder(buyerToken, productId);
        approvePayment(buyerToken, orderId);

        cancelPayment(buyerToken, orderId);
        cancelPayment(buyerToken, orderId);

        assertInventory(productId, 5, 0);
        assertThat(countInventoryAdjustments(orderId, "RELEASE")).isEqualTo(1);
    }

    @Test
    void 재고형_결제_승인_실패는_예약을_한번만_해제하고_실패를_응답한다() throws Exception {
        signupAndLogin("failed-stock-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("failed-stock-buyer@example.com", "password123", "buyer");
        Long productId = createStockProduct("failed-stock-seller@example.com", 5);
        Long orderId = createOrder(buyerToken, productId);
        doThrow(new PaymentGatewayException("gateway rejected"))
                .when(paymentGateway).approve(orderId, 10_000L);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));
        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));

        assertInventory(productId, 5, 0);
        assertThat(countInventoryAdjustments(orderId, "RELEASE")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                orderId
        )).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                Long.class,
                orderId
        )).isZero();
    }

    @Test
    void 주문자는_결제_승인에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.externalPaymentId").value("fake-payment-" + orderId))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.approvedAt").exists())
                .andExpect(jsonPath("$.data.canceledAt").doesNotExist());
    }

    @Test
    void 쿠폰_결제_승인에_성공하면_예약을_소비하고_쿠폰을_사용_처리한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-payment-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-payment-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-payment-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCouponId").value(couponId))
                .andExpect(jsonPath("$.data.couponDiscountAmount").value(1_000L));

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("USED");
    }

    @Test
    void 쿠폰_결제_승인에_실패하면_예약을_해제하고_쿠폰은_발급_상태를_유지한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-failure-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-failure-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-failure-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);
        doThrow(new PaymentGatewayException("gateway rejected"))
                .when(paymentGateway).approve(orderId, 9_000L);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("ISSUED");
    }

    @Test
    void 쿠폰_결제_승인_실패_보상은_예약_해제와_주문_취소와_재고_해제를_함께_처리한다() throws Exception {
        signupAndLogin("coupon-stock-failure-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-stock-failure-buyer@example.com", "password123", "buyer");
        Long productId = createStockProduct("coupon-stock-failure-seller@example.com", 5);
        Long couponId = issueFixedAmountCoupon("coupon-stock-failure-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);
        doThrow(new PaymentGatewayException("gateway rejected"))
                .when(paymentGateway).approve(orderId, 9_000L);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("ISSUED");
        assertThat(jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId))
                .isEqualTo("CANCELED");
        assertInventory(productId, 5, 0);
    }

    @Test
    void 결제_실패_보상에서_재고_해제가_실패하면_쿠폰_예약_해제도_롤백한다() throws Exception {
        signupAndLogin("coupon-compensation-rollback-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-compensation-rollback-buyer@example.com", "password123", "buyer");
        Long productId = createStockProduct("coupon-compensation-rollback-seller@example.com", 5);
        Long couponId = issueFixedAmountCoupon("coupon-compensation-rollback-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);
        doThrow(new IllegalStateException("inventory release failed"))
                .when(inventoryService).releaseForFailedPaymentApproval(any());

        assertThatThrownBy(() -> paymentFailureCompensationService.compensate(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inventory release failed");

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("RESERVED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("ISSUED");
        assertThat(jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId))
                .isEqualTo("CREATED");
        assertInventory(productId, 5, 1);
    }

    @Test
    void 만료된_쿠폰_예약은_결제_승인할_수_없고_소비되지_않는다() throws Exception {
        String sellerToken = signupAndLogin("coupon-expired-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-expired-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-expired-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);
        jdbcTemplate.update("update coupon_reservations set expires_at = current_timestamp - interval '1 second' where order_id = ?", orderId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));

        verifyNoInteractions(paymentGateway);
        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isNotEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("ISSUED");
    }

    @Test
    void 최종_결제금액이_0원이면_외부_결제없이_내부_결제를_승인한다() throws Exception {
        String sellerToken = signupAndLogin("zero-payment-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("zero-payment-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("zero-payment-buyer@example.com", 10_000L);

        Long orderId = createCouponOrder(buyerToken, productId, couponId);

        verifyNoInteractions(paymentGateway);
        assertThat(jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId))
                .isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("select external_payment_id from payments where order_id = ?", String.class, orderId))
                .isEqualTo("INTERNAL_ZERO_AMOUNT:" + orderId);
        assertThat(jdbcTemplate.queryForObject("select status from payments where order_id = ?", String.class, orderId))
                .isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject("select status from coupon_reservations where order_id = ?", String.class, orderId))
                .isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject("select status from member_coupons where id = ?", String.class, couponId))
                .isEqualTo("USED");
    }

    @Test
    void 취소된_쿠폰_주문은_외부_결제_호출없이_승인을_거절하고_예약과_쿠폰을_유지한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-canceled-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-canceled-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-canceled-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());
        String reservationStatusBeforeApproval = jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        );
        String couponStatusBeforeApproval = jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        );
        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));

        verifyNoInteractions(paymentGateway);
        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo(reservationStatusBeforeApproval);
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo(couponStatusBeforeApproval);
    }

    @Test
    void 쿠폰_결제는_한번만_승인되고_재승인해도_소비_상태와_결제_건수가_변하지_않는다() throws Exception {
        String sellerToken = signupAndLogin("coupon-repeat-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-repeat-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-repeat-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);
        approvePayment(buyerToken, orderId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("USED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments where order_id = ?", Long.class, orderId))
                .isEqualTo(1L);
    }

    @Test
    void 결제_취소_후에도_소비된_쿠폰은_사용_상태를_유지한다() throws Exception {
        String sellerToken = signupAndLogin("coupon-cancel-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("coupon-cancel-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, 10_000L);
        Long couponId = issueFixedAmountCoupon("coupon-cancel-buyer@example.com", 1_000L);
        Long orderId = createCouponOrder(buyerToken, productId, couponId);
        approvePayment(buyerToken, orderId);

        cancelPayment(buyerToken, orderId);

        assertThat(jdbcTemplate.queryForObject(
                "select status from coupon_reservations where order_id = ?", String.class, orderId
        )).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                "select status from member_coupons where id = ?", String.class, couponId
        )).isEqualTo("USED");
    }

    @Test
    void 프로모션_종료와_상품가격_변경_후에도_결제와_구매자_주문은_가격_스냅샷을_사용한다() throws Exception {
        String sellerToken = signupAndLogin("snapshot-payment-seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("snapshot-payment-buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long promotionId = createStoreWidePromotion(productId, 500_000L);
        Long orderId = createOrder(buyerToken, productId);

        jdbcTemplate.update("update products set price = ? where id = ?", 300_000L, productId);
        jdbcTemplate.update("update promotion_campaigns set lifecycle_status = 'ENDED' where id = ?", promotionId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));
        verify(paymentGateway).approve(orderId, 1_500_000L);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productPrice").value(1_500_000L))
                .andExpect(jsonPath("$.data.listPrice").value(2_000_000L))
                .andExpect(jsonPath("$.data.promotionCampaignId").value(promotionId))
                .andExpect(jsonPath("$.data.promotionDiscountAmount").value(500_000L))
                .andExpect(jsonPath("$.data.finalPrice").value(1_500_000L));
    }

    @Test
    void 결제_승인은_JWT가_필요하다() throws Exception {
        mockMvc.perform(post("/api/payments/{orderId}/approve", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 주문자가_아니면_결제_승인에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PAYMENT_ACCESS_DENIED"));
    }

    @Test
    void 이미_승인된_주문은_다시_결제_승인할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        approvePayment(buyerToken, orderId);

        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_APPROVE_NOT_ALLOWED"));
    }

    @Test
    void 주문자는_결제_취소에_성공한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        Long paymentId = approvePayment(buyerToken, orderId);

        mockMvc.perform(post("/api/payments/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledAt").exists());

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
    }

    @Test
    void 결제_취소는_주문을_먼저_잠근_뒤_결제를_잠근다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        approvePayment(buyerToken, orderId);

        mockMvc.perform(post("/api/payments/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        InOrder lockOrder = inOrder(orderRepository, paymentRepository);
        lockOrder.verify(orderRepository).findStateChangeTargetById(orderId);
        lockOrder.verify(paymentRepository).findStateChangeTargetByOrderId(orderId);
    }

    @Test
    void 배송_시작_후에는_외부_결제_취소를_호출하지_않고_결제_취소에_실패한다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken);
        Long orderId = createOrder(buyerToken, productId);
        approvePayment(buyerToken, orderId);
        startDelivery(buyerToken, orderId);

        mockMvc.perform(post("/api/payments/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_NOT_ALLOWED"));

        verify(paymentGateway, never()).cancel("fake-payment-" + orderId);
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

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private Long createProduct(String accessToken) throws Exception {
        return createProduct(accessToken, 2_000_000L);
    }

    private Long createProduct(String accessToken, long price) throws Exception {
        Long storeId = activePersonalStoreId(accessToken);
        Long uploadId = uploadImage(accessToken, "macbook-1.jpg");

        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": %d,
                                  "title": "MacBook Pro",
                                  "description": "M3 laptop",
                                  "price": %d,
                                  "salesPolicy": "SINGLE_ITEM",
                                  "images": [
                                    {
                                      "uploadId": %d,
                                      "sortOrder": 0,
                                      "representative": true
                                    }
                                  ]
                                }
                                """.formatted(storeId, price, uploadId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
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

    private Long createStoreWidePromotion(Long productId, long discountAmount) {
        Long storeId = jdbcTemplate.queryForObject("select store_id from products where id = ?", Long.class, productId);
        jdbcTemplate.update("update stores set type = 'BUSINESS' where id = ?", storeId);
        return jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', ?, 10, '결제 스냅샷 할인',
                    current_timestamp - interval '1 minute', current_timestamp + interval '1 minute', 'DRAFT',
                    current_timestamp, current_timestamp)
                returning id
                """, Long.class, storeId, discountAmount);
    }

    private void cancelPayment(String accessToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/payments/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
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

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
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

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private Long createCouponOrder(String accessToken, Long productId, Long couponId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"memberCouponId\":%d}".formatted(productId, couponId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long issueFixedAmountCoupon(String buyerEmail, long discountAmount) {
        Long campaignId = jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (
                    version, owner_type, scope, discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at,
                    validity_type, validity_days, lifecycle_status, issued_count, created_at, updated_at
                ) values (
                    0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', ?, null,
                    0, true, '결제 쿠폰', current_timestamp - interval '1 day', current_timestamp + interval '1 day',
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

    private Long approvePayment(String accessToken, Long orderId) throws Exception {
        String response = mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private void startDelivery(String accessToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/deliveries/{orderId}/start", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
