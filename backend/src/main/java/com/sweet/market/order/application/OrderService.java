package com.sweet.market.order.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.application.CouponReservationQuote;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderDomainError;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.application.PaymentApprovalTransactionService;
import com.sweet.market.payment.application.PaymentGateway;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.domain.PaymentStatus;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductDomainError;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.application.PromotionPrice;
import com.sweet.market.promotion.application.PromotionPricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final InventoryService inventoryService;
    private final PromotionPricingService promotionPricingService;
    private final CouponRedemptionService couponRedemptionService;
    private final PaymentApprovalTransactionService paymentApprovalTransactionService;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            InventoryService inventoryService,
            PromotionPricingService promotionPricingService,
            CouponRedemptionService couponRedemptionService,
            PaymentApprovalTransactionService paymentApprovalTransactionService
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.inventoryService = inventoryService;
        this.promotionPricingService = promotionPricingService;
        this.couponRedemptionService = couponRedemptionService;
        this.paymentApprovalTransactionService = paymentApprovalTransactionService;
    }

    @Transactional
    public OrderResponse create(Long buyerId, Long productId) {
        return create(buyerId, productId, null);
    }

    @Transactional
    public OrderResponse create(Long buyerId, Long productId, Long memberCouponId) {
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Product product = productRepository.findWithStoreAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Order order;
        CouponReservationQuote couponReservationQuote = null;
        try {
            if (!product.isPurchasable()) {
                throw new DomainException(OrderDomainError.PRODUCT_NOT_PURCHASABLE);
            }
            PromotionPrice promotionPrice = promotionPricingService.quote(product);
            if (memberCouponId != null) {
                Instant now = Instant.now();
                couponReservationQuote = couponRedemptionService.quoteForReservation(
                        buyerId, memberCouponId, product, promotionPrice, now
                );
                order = Order.create(buyer, product, promotionPrice, couponReservationQuote.discountQuote());
            } else {
                order = Order.create(buyer, product, promotionPrice);
            }
        } catch (DomainException exception) {
            if (exception.error() == ProductDomainError.NOT_ON_SALE
                    || exception.error() == OrderDomainError.PRODUCT_NOT_PURCHASABLE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, exception);
            }
            throw exception;
        }

        Order savedOrder = orderRepository.save(order);
        inventoryService.reserveForOrder(savedOrder);
        if (couponReservationQuote != null) {
            couponRedemptionService.reserve(couponReservationQuote, savedOrder, Instant.now());
        }
        if (savedOrder.getFinalPrice() == 0L) {
            paymentApprovalTransactionService.approveWithoutGateway(buyerId, savedOrder.getId());
        }
        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse cancel(Long buyerId, Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.CANCELED) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            couponRedemptionService.releaseForCanceledOrder(order, Instant.now());
            order.cancel();
            inventoryService.releaseForPreShippingExit(order);
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        Payment payment = paymentRepository.findStateChangeTargetByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        try {
            if (payment.getStatus() == PaymentStatus.APPROVED && !payment.canCancel()) {
                throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
            }
            if (payment.canCancel()) {
                paymentGateway.cancel(payment.getExternalPaymentId());
            }
            payment.cancel();
            inventoryService.releaseForPreShippingExit(order);
        } catch (BusinessException exception) {
            throw exception;
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED, exception);
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse confirm(Long buyerId, Long orderId) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        try {
            order.confirm();
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.ORDER_CONFIRM_NOT_ALLOWED, exception);
        }

        return OrderResponse.from(order);
    }
}
