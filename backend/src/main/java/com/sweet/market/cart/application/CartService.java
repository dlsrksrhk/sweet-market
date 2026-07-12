package com.sweet.market.cart.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.cart.api.CartCheckoutResponse;
import com.sweet.market.cart.api.CartResponse;
import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.order.api.OrderSummaryResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final TransactionTemplate insertTransaction;

    public CartService(
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            OrderRepository orderRepository,
            InventoryService inventoryService,
            PlatformTransactionManager transactionManager
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CartResponse add(Long buyerId, Long productId) {
        Product product = productRepository.findWithStoreById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateCartable(buyerId, product);

        if (cartItemRepository.existsByBuyerIdAndProductId(buyerId, productId)) {
            return new CartResponse(productId, true);
        }

        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        try {
            insertTransaction.executeWithoutResult(status ->
                    cartItemRepository.saveAndFlush(CartItem.create(buyer, product))
            );
        } catch (DataIntegrityViolationException exception) {
            if (cartItemRepository.findByBuyerIdAndProductId(buyerId, productId).isEmpty()) {
                throw exception;
            }
        }

        return new CartResponse(productId, true);
    }

    @Transactional
    public CartResponse remove(Long buyerId, Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        cartItemRepository.deleteByBuyerIdAndProductId(buyerId, productId);
        return new CartResponse(productId, false);
    }

    @Transactional
    public CartCheckoutResponse checkout(Long buyerId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_EMPTY);
        }

        Set<Long> uniqueIds = new HashSet<>(cartItemIds);
        if (uniqueIds.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
        }

        List<CartItem> cartItems = cartItemRepository.findAllWithBuyerProductSellerImagesByIdIn(cartItemIds);
        if (cartItems.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
        }

        if (cartItems.stream().anyMatch(cartItem -> !cartItem.getBuyer().getId().equals(buyerId))) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_INVALID_ITEMS);
        }

        if (cartItems.stream().anyMatch(cartItem -> isCheckoutNotAllowed(buyerId, cartItem))) {
            throw new BusinessException(ErrorCode.CART_CHECKOUT_NOT_ALLOWED);
        }

        List<Order> orders = cartItems.stream()
                .map(cartItem -> Order.create(cartItem.getBuyer(), cartItem.getProduct()))
                .map(this::saveAndReserve)
                .toList();
        cartItemRepository.deleteAll(cartItems);

        return new CartCheckoutResponse(orders.stream()
                .map(OrderSummaryResponse::from)
                .toList());
    }

    private void validateCartable(Long buyerId, Product product) {
        if (product.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.CART_OWN_PRODUCT_NOT_ALLOWED);
        }
        if (!product.isPurchasable() || !inventoryService.isAvailableForOrder(product)) {
            throw new BusinessException(ErrorCode.CART_PRODUCT_NOT_ON_SALE);
        }
    }

    private boolean isCheckoutNotAllowed(Long buyerId, CartItem cartItem) {
        Product product = cartItem.getProduct();
        return product.isOwnedBy(buyerId)
                || !product.isPurchasable()
                || !inventoryService.isAvailableForOrder(product);
    }

    private Order saveAndReserve(Order order) {
        Order savedOrder = orderRepository.save(order);
        try {
            inventoryService.reserveForOrder(savedOrder);
        } catch (BusinessException exception) {
            if (exception.errorCode() == ErrorCode.PRODUCT_NOT_ON_SALE) {
                throw new BusinessException(ErrorCode.CART_CHECKOUT_NOT_ALLOWED);
            }
            throw exception;
        }
        return savedOrder;
    }
}
