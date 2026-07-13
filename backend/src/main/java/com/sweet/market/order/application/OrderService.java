package com.sweet.market.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.inventory.application.InventoryService;
import com.sweet.market.order.api.OrderResponse;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.application.PaymentGateway;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.domain.PaymentStatus;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final InventoryService inventoryService;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            InventoryService inventoryService
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public OrderResponse create(Long buyerId, Long productId) {
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Product product = productRepository.findWithStoreAndImagesById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        Order order;
        try {
            order = Order.create(buyer, product);
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, exception);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, exception);
        }

        Order savedOrder = orderRepository.save(order);
        inventoryService.reserveForOrder(savedOrder);
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
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
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
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.ORDER_CONFIRM_NOT_ALLOWED);
        }

        return OrderResponse.from(order);
    }
}
