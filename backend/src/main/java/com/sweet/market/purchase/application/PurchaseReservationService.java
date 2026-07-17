package com.sweet.market.purchase.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.common.error.ErrorResponse;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.cart.api.CartCheckoutResponse;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.coupon.application.CouponRedemptionService;
import com.sweet.market.coupon.application.CouponReservationQuote;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderDomainError;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.event.OperationalFailureRecorder;
import com.sweet.market.operations.inventory.InventoryOutcomeEventFactory;
import com.sweet.market.operations.purchase.PurchaseOutcomeEventFactory;
import com.sweet.market.operations.purchase.PurchaseOutcomeReason;
import com.sweet.market.payment.application.PaymentApprovalTransactionService;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductDomainError;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.application.PromotionPrice;
import com.sweet.market.promotion.application.PromotionPricingService;
import com.sweet.market.purchase.api.CartCheckoutFailure;
import com.sweet.market.purchase.api.CartCheckoutFailureException;
import com.sweet.market.purchase.api.CartCheckoutFailureItem;
import com.sweet.market.purchase.api.CartCheckoutFailureResponse;
import com.sweet.market.store.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PurchaseReservationService {

    private final PurchaseRequestService requestService;
    private final CartItemRepository cartItemRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final PromotionPricingService promotionPricingService;
    private final CouponRedemptionService couponRedemptionService;
    private final ProductReservationService productReservationService;
    private final PaymentApprovalTransactionService paymentApprovalTransactionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final InventoryRepository inventoryRepository;
    private final OperationalEventRecorder operationalEventRecorder;
    private final OperationalFailureRecorder operationalFailureRecorder;
    private final PurchaseOutcomeEventFactory purchaseOutcomeEventFactory;
    private final InventoryOutcomeEventFactory inventoryOutcomeEventFactory;

    public PurchaseReservationService(
            PurchaseRequestService requestService,
            CartItemRepository cartItemRepository,
            MemberRepository memberRepository,
            ProductRepository productRepository,
            StoreRepository storeRepository,
            OrderRepository orderRepository,
            PromotionPricingService promotionPricingService,
            CouponRedemptionService couponRedemptionService,
            ProductReservationService productReservationService,
            PaymentApprovalTransactionService paymentApprovalTransactionService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher eventPublisher,
            InventoryRepository inventoryRepository,
            OperationalEventRecorder operationalEventRecorder,
            OperationalFailureRecorder operationalFailureRecorder,
            PurchaseOutcomeEventFactory purchaseOutcomeEventFactory,
            InventoryOutcomeEventFactory inventoryOutcomeEventFactory
    ) {
        this.requestService = requestService;
        this.cartItemRepository = cartItemRepository;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
        this.promotionPricingService = promotionPricingService;
        this.couponRedemptionService = couponRedemptionService;
        this.productReservationService = productReservationService;
        this.paymentApprovalTransactionService = paymentApprovalTransactionService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.eventPublisher = eventPublisher;
        this.inventoryRepository = inventoryRepository;
        this.operationalEventRecorder = operationalEventRecorder;
        this.operationalFailureRecorder = operationalFailureRecorder;
        this.purchaseOutcomeEventFactory = purchaseOutcomeEventFactory;
        this.inventoryOutcomeEventFactory = inventoryOutcomeEventFactory;
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
            recordPurchaseFailure(command.productId(), reason(exception.errorCode()));
            throw exception;
        }
    }

    public CartCheckoutResponse purchaseCart(CartPurchaseCommand command, String idempotencyKey) {
        PurchaseRequestService.Claim claim = requestService.claim(
                command.buyerId(), idempotencyKey, cartFingerprint(command.cartItemIds()), Instant.now()
        );
        if (claim instanceof PurchaseRequestService.Claim.Replay replay) {
            return replayCart(replay);
        }

        UUID executionToken = ((PurchaseRequestService.Claim.New) claim).executionToken();
        try {
            CartCheckoutResponse response = transactionTemplate.execute(status -> reserveCart(command));
            requestService.completeSuccess(
                    command.buyerId(), idempotencyKey, executionToken, 200,
                    objectMapper.valueToTree(ApiResponse.ok(response)), Instant.now()
            );
            return response;
        } catch (CartCheckoutFailureException exception) {
            CartCheckoutFailureResponse response = cartFailureResponse(exception.failure());
            requestService.completeBusinessFailure(
                    command.buyerId(), idempotencyKey, executionToken, 409,
                    objectMapper.valueToTree(response), Instant.now()
            );
            exception.failure().items().forEach(item -> recordPurchaseFailure(
                    item.productId(), item.reason() == CartCheckoutFailureItem.Reason.SOLD_OUT
                            ? PurchaseOutcomeReason.SOLD_OUT
                            : PurchaseOutcomeReason.PRODUCT_UNAVAILABLE));
            throw exception;
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

    private CartCheckoutResponse replayCart(PurchaseRequestService.Claim.Replay replay) {
        if (replay.httpStatus() == 200) {
            return objectMapper.convertValue(replay.payload().path("data"), CartCheckoutResponse.class);
        }
        if ("CART_CHECKOUT_NOT_ALLOWED".equals(replay.payload().path("code").asText())) {
            CartCheckoutFailure failure = objectMapper.convertValue(
                    replay.payload().path("data"), CartCheckoutFailure.class
            );
            throw new CartCheckoutFailureException(failure);
        }

        throw new BusinessException(ErrorCode.valueOf(replay.payload().path("code").asText()));
    }

    private CartCheckoutResponse reserveCart(CartPurchaseCommand command) {
        List<Long> cartItemIds = command.cartItemIds();
        validateCartItemIds(cartItemIds);
        List<CartItem> cartItems = cartItemRepository.findAllWithBuyerProductSellerImagesByIdIn(cartItemIds);
        validateCartItems(command.buyerId(), cartItemIds, cartItems);

        List<CartItem> orderedCartItems = cartItems.stream()
                .sorted(Comparator.comparing(cartItem -> cartItem.getProduct().getId()))
                .toList();
        Map<Long, Product> lockedProductsById = lockCartReservationTargets(orderedCartItems);
        List<Long> orderIds = orderedCartItems.stream()
                .map(cartItem -> createAndReserveCartOrder(
                        cartItem,
                        lockedProductsById.get(cartItem.getProduct().getId())
                ))
                .map(Order::getId)
                .toList();

        cartItemRepository.deleteAllByIdInBatch(orderedCartItems.stream().map(CartItem::getId).toList());
        CartCheckoutResponse response = new CartCheckoutResponse(orderIds.stream()
                .map(orderId -> orderRepository.findWithBuyerAndProductById(orderId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)))
                .map(com.sweet.market.order.api.OrderSummaryResponse::from)
                .toList());
        invalidateDiscovery();
        return response;
    }

    private void validateCartItemIds(List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_EMPTY);
        }
        if (new HashSet<>(cartItemIds).size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
        }
    }

    private void validateCartItems(Long buyerId, List<Long> cartItemIds, List<CartItem> cartItems) {
        if (cartItems.size() != cartItemIds.size()
                || cartItems.stream().anyMatch(cartItem -> !cartItem.getBuyer().getId().equals(buyerId))) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
        }
    }

    private Map<Long, Product> lockCartReservationTargets(List<CartItem> cartItems) {
        List<Long> storeIds = cartItems.stream()
                .map(cartItem -> cartItem.getProduct().getStore().getId())
                .distinct()
                .sorted()
                .toList();
        List<Long> productIds = cartItems.stream()
                .map(cartItem -> cartItem.getProduct().getId())
                .toList();
        storeRepository.findAllByIdInForUpdateOrderByIdAsc(storeIds);
        return productRepository.findAllByIdInForUpdateOrderByIdAsc(productIds).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, product -> product));
    }

    private Product lockDirectReservationTarget(Product product) {
        storeRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(product.getStore().getId()));
        return productRepository.findWithStoreForUpdate(product.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE));
    }

    private Order createAndReserveCartOrder(CartItem cartItem, Product product) {
        try {
            PromotionPrice price = promotionPricingService.quote(product);
            Order order = orderRepository.saveAndFlush(Order.create(cartItem.getBuyer(), product, price));
            recordOrderCreated(order, null, Instant.now());
            productReservationService.reserve(order);
            return order;
        } catch (BusinessException | DomainException exception) {
            throw cartCheckoutFailure(cartItem, exception);
        }
    }

    private CartCheckoutFailureException cartCheckoutFailure(CartItem cartItem, RuntimeException exception) {
        CartCheckoutFailureItem.Reason reason = exception instanceof BusinessException businessException
                && businessException.errorCode() == ErrorCode.PRODUCT_SOLD_OUT
                ? CartCheckoutFailureItem.Reason.SOLD_OUT
                : CartCheckoutFailureItem.Reason.UNAVAILABLE;
        return new CartCheckoutFailureException(new CartCheckoutFailure(List.of(new CartCheckoutFailureItem(
                cartItem.getId(),
                cartItem.getProduct().getId(),
                cartItem.getProduct().getTitle(),
                reason
        ))));
    }

    private String cartFingerprint(List<Long> cartItemIds) {
        return "cart:" + cartItemIds.stream().sorted().map(String::valueOf).reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private CartCheckoutFailureResponse cartFailureResponse(CartCheckoutFailure failure) {
        return new CartCheckoutFailureResponse(
                ErrorCode.CART_CHECKOUT_NOT_ALLOWED.name(),
                ErrorCode.CART_CHECKOUT_NOT_ALLOWED.message(),
                failure
        );
    }

    private OrderResponse reserveDirect(DirectPurchaseCommand command) {
        Member buyer = memberRepository.findById(command.buyerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Product product = productRepository.findWithStoreAndImagesById(command.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Product lockedProduct = lockDirectReservationTarget(product);

        try {
            if (!lockedProduct.isPurchasable()) {
                throw new DomainException(OrderDomainError.PRODUCT_NOT_PURCHASABLE);
            }
            PromotionPrice promotionPrice = promotionPricingService.quote(lockedProduct);
            CouponReservationQuote couponReservationQuote = command.memberCouponId() == null ? null
                    : couponRedemptionService.quoteForReservation(
                            command.buyerId(), command.memberCouponId(), lockedProduct, promotionPrice, Instant.now()
                    );
            Order order = couponReservationQuote == null
                    ? Order.create(buyer, lockedProduct, promotionPrice)
                    : Order.create(buyer, lockedProduct, promotionPrice, couponReservationQuote.discountQuote());
            Order savedOrder = orderRepository.saveAndFlush(order);

            Long couponCampaignId = couponReservationQuote == null
                    ? null : couponReservationQuote.memberCoupon().getCampaign().getId();
            recordOrderCreated(savedOrder, couponCampaignId, Instant.now());

            productReservationService.reserve(savedOrder);
            if (couponReservationQuote != null) {
                CouponReservationQuote revalidatedCouponReservationQuote = couponRedemptionService.quoteForReservation(
                        command.buyerId(), command.memberCouponId(), lockedProduct, promotionPrice, Instant.now()
                );
                couponRedemptionService.reserve(revalidatedCouponReservationQuote, savedOrder, Instant.now());
            }
            if (savedOrder.getFinalPrice() == 0L) {
                paymentApprovalTransactionService.approveWithoutGateway(command.buyerId(), savedOrder.getId());
            }
            Order reservedOrder = orderRepository.findWithBuyerAndProductById(savedOrder.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            OrderResponse response = OrderResponse.from(reservedOrder);
            invalidateDiscovery();
            return response;
        } catch (DomainException exception) {
            if (exception.error() == ProductDomainError.NOT_ON_SALE
                    || exception.error() == OrderDomainError.PRODUCT_NOT_PURCHASABLE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, exception);
            }
            throw exception;
        }
    }

    private void invalidateDiscovery() {
        eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
    }

    private void recordOrderCreated(Order order, Long couponCampaignId, Instant occurredAt) {
        operationalEventRecorder.record(purchaseOutcomeEventFactory.orderCreated(
                order.getId(), order.getProduct().getStore().getId(), order.getProduct().getId(),
                order.getPromotionCampaignId(), couponCampaignId,
                order.getPromotionDiscountAmount(), order.getCouponDiscountAmount(), occurredAt));
    }

    private void recordPurchaseFailure(Long productId, PurchaseOutcomeReason reason) {
        if (reason == null) {
            return;
        }
        productRepository.findWithStoreById(productId).ifPresent(product -> {
            Instant occurredAt = Instant.now();
            operationalFailureRecorder.recordSafely(purchaseOutcomeEventFactory.purchaseFailed(
                    product.getStore().getId(), product.getId(), reason, occurredAt));
            if (reason == PurchaseOutcomeReason.SOLD_OUT
                    || reason == PurchaseOutcomeReason.PRODUCT_UNAVAILABLE) {
                Inventory inventory = product.isSingleItem() ? null
                        : inventoryRepository.findByProductId(productId).orElse(null);
                Integer availableQuantity = inventory == null ? null : inventory.getAvailableQuantity();
                long aggregateVersion = inventory == null ? product.getVersion() : inventory.getVersion();
                boolean soldOut = product.isSingleItem()
                        ? !product.isPurchasable()
                        : availableQuantity != null && availableQuantity == 0;
                operationalFailureRecorder.recordSafely(inventoryOutcomeEventFactory.outcome(
                        "RESERVATION_FAILED", product.getId(), product.getStore().getId(),
                        product.getSalesPolicy().name(), availableQuantity, soldOut,
                        aggregateVersion, occurredAt));
            }
        });
    }

    private PurchaseOutcomeReason reason(ErrorCode errorCode) {
        return switch (errorCode) {
            case PRODUCT_SOLD_OUT -> PurchaseOutcomeReason.SOLD_OUT;
            case PRODUCT_UNAVAILABLE, PRODUCT_NOT_ON_SALE -> PurchaseOutcomeReason.PRODUCT_UNAVAILABLE;
            case MEMBER_COUPON_NOT_FOUND, MEMBER_COUPON_ACCESS_DENIED, MEMBER_COUPON_NOT_ISSUED,
                 MEMBER_COUPON_EXPIRED, MEMBER_COUPON_TARGET_MISMATCH,
                 MEMBER_COUPON_MINIMUM_PURCHASE_NOT_MET,
                 MEMBER_COUPON_PROMOTION_STACKING_NOT_ALLOWED,
                 MEMBER_COUPON_ALREADY_RESERVED -> PurchaseOutcomeReason.COUPON_REJECTED;
            default -> null;
        };
    }
}
