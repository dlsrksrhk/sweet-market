package com.sweet.market.purchase.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.common.error.ErrorResponse;
import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.application.CouponReservationQuote;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderDomainError;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.application.PaymentApprovalTransactionService;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductDomainError;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.application.PromotionPrice;
import com.sweet.market.promotion.application.PromotionPricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PurchaseReservationService {

    private final PurchaseRequestService requestService;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PromotionPricingService promotionPricingService;
    private final CouponRedemptionService couponRedemptionService;
    private final ProductReservationService productReservationService;
    private final PaymentApprovalTransactionService paymentApprovalTransactionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PurchaseReservationService(
            PurchaseRequestService requestService,
            MemberRepository memberRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PromotionPricingService promotionPricingService,
            CouponRedemptionService couponRedemptionService,
            ProductReservationService productReservationService,
            PaymentApprovalTransactionService paymentApprovalTransactionService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.requestService = requestService;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.promotionPricingService = promotionPricingService;
        this.couponRedemptionService = couponRedemptionService;
        this.productReservationService = productReservationService;
        this.paymentApprovalTransactionService = paymentApprovalTransactionService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public OrderResponse purchaseDirect(DirectPurchaseCommand command, String idempotencyKey) {
        String fingerprint = "direct:%d:%s".formatted(
                command.productId(), Objects.toString(command.memberCouponId(), "")
        );
        PurchaseRequestService.Claim claim = requestService.claim(
                command.buyerId(), idempotencyKey, fingerprint, Instant.now()
        );
        if (claim instanceof PurchaseRequestService.Claim.Replay replay) {
            return replay(replay);
        }

        UUID executionToken = ((PurchaseRequestService.Claim.New) claim).executionToken();
        try {
            OrderResponse response = transactionTemplate.execute(status -> reserveDirect(command));
            requestService.completeSuccess(
                    command.buyerId(), idempotencyKey, executionToken, 201,
                    objectMapper.valueToTree(ApiResponse.ok(response)), Instant.now()
            );
            return response;
        } catch (BusinessException exception) {
            requestService.completeBusinessFailure(
                    command.buyerId(), idempotencyKey, executionToken,
                    exception.errorCode().status().value(),
                    objectMapper.valueToTree(ErrorResponse.of(exception.errorCode())),
                    Instant.now()
            );
            throw exception;
        }
    }

    private OrderResponse replay(PurchaseRequestService.Claim.Replay replay) {
        if (replay.httpStatus() == 201) {
            return objectMapper.convertValue(replay.payload().path("data"), OrderResponse.class);
        }

        JsonNode code = replay.payload().path("code");
        throw new BusinessException(ErrorCode.valueOf(code.asText()));
    }

    private OrderResponse reserveDirect(DirectPurchaseCommand command) {
        Member buyer = memberRepository.findById(command.buyerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Product product = productRepository.findWithStoreAndImagesById(command.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        try {
            if (!product.isPurchasable()) {
                throw new DomainException(OrderDomainError.PRODUCT_NOT_PURCHASABLE);
            }
            PromotionPrice promotionPrice = promotionPricingService.quote(product);
            CouponReservationQuote couponReservationQuote = command.memberCouponId() == null ? null
                    : couponRedemptionService.quoteForReservation(
                            command.buyerId(), command.memberCouponId(), product, promotionPrice, Instant.now()
                    );
            Order order = couponReservationQuote == null
                    ? Order.create(buyer, product, promotionPrice)
                    : Order.create(buyer, product, promotionPrice, couponReservationQuote.discountQuote());
            Order savedOrder = orderRepository.saveAndFlush(order);

            productReservationService.reserve(savedOrder);
            if (couponReservationQuote != null) {
                CouponReservationQuote revalidatedCouponReservationQuote = couponRedemptionService.quoteForReservation(
                        command.buyerId(), command.memberCouponId(), product, promotionPrice, Instant.now()
                );
                couponRedemptionService.reserve(revalidatedCouponReservationQuote, savedOrder, Instant.now());
            }
            if (savedOrder.getFinalPrice() == 0L) {
                paymentApprovalTransactionService.approveWithoutGateway(command.buyerId(), savedOrder.getId());
            }
            Order reservedOrder = orderRepository.findWithBuyerAndProductById(savedOrder.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            return OrderResponse.from(reservedOrder);
        } catch (DomainException exception) {
            if (exception.error() == ProductDomainError.NOT_ON_SALE
                    || exception.error() == OrderDomainError.PRODUCT_NOT_PURCHASABLE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, exception);
            }
            throw exception;
        }
    }
}
