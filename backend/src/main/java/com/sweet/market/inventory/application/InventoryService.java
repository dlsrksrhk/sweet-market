package com.sweet.market.inventory.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.discovery.cache.DiscoveryInvalidationEvent;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.domain.InventoryAdjustment;
import com.sweet.market.inventory.domain.InventoryChangeType;
import com.sweet.market.inventory.domain.InventoryDomainError;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.operations.event.OperationalEventRecorder;
import com.sweet.market.operations.inventory.InventoryOutcomeEventFactory;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.application.StoreAccessService;
import org.hibernate.StaleObjectStateException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final StoreAccessService storeAccessService;
    private final InventoryAdjustmentTransactionService inventoryAdjustmentTransactionService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OperationalEventRecorder operationalEventRecorder;
    private final InventoryOutcomeEventFactory inventoryOutcomeEventFactory;

    @Autowired
    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            StoreAccessService storeAccessService,
            InventoryAdjustmentTransactionService inventoryAdjustmentTransactionService,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher,
            OperationalEventRecorder operationalEventRecorder,
            InventoryOutcomeEventFactory inventoryOutcomeEventFactory
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.storeAccessService = storeAccessService;
        this.inventoryAdjustmentTransactionService = inventoryAdjustmentTransactionService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.operationalEventRecorder = operationalEventRecorder;
        this.inventoryOutcomeEventFactory = inventoryOutcomeEventFactory;
    }

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            StoreAccessService storeAccessService,
            InventoryAdjustmentTransactionService inventoryAdjustmentTransactionService,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this(inventoryRepository, inventoryAdjustmentRepository, storeAccessService,
                inventoryAdjustmentTransactionService, orderRepository, eventPublisher,
                event -> { }, null);
    }

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            StoreAccessService storeAccessService,
            InventoryAdjustmentTransactionService inventoryAdjustmentTransactionService,
            OrderRepository orderRepository
    ) {
        this(inventoryRepository, inventoryAdjustmentRepository, storeAccessService, inventoryAdjustmentTransactionService,
                orderRepository, event -> { });
    }

    public void initialize(Product product, int initialTotalQuantity, Long memberId) {
        Inventory inventory = inventoryRepository.save(Inventory.initialize(product, initialTotalQuantity));
        inventoryAdjustmentRepository.saveAndFlush(inventory.getInitializationAdjustment());
        recordInventoryOutcome("INITIALIZE", inventory, inventory.getAvailableQuantity(),
                inventory.getAvailableQuantity() == 0, Instant.now());
    }

    @Transactional(readOnly = true)
    public boolean isAvailableForOrder(Product product) {
        if (product.isSingleItem()) {
            return product.isPurchasable();
        }
        return findInventory(product).getAvailableQuantity() > 0;
    }

    @Transactional
    public void reserveForOrder(Order order) {
        if (order.getProduct().isSingleItem()) {
            return;
        }
        if (hasAdjustment(order, InventoryChangeType.RESERVATION)) {
            return;
        }

        InventoryAdjustment adjustment;
        try {
            adjustment = findInventory(order.getProduct()).reserve(order);
        } catch (DomainException exception) {
            ErrorCode errorCode = switch ((InventoryDomainError) exception.error()) {
                case STOCK_UNAVAILABLE -> ErrorCode.PRODUCT_NOT_ON_SALE;
                default -> ErrorCode.VALIDATION_ERROR;
            };
            throw new BusinessException(errorCode, exception);
        }
        inventoryAdjustmentRepository.save(adjustment);
        eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
    }

    @Transactional
    public void releaseForPreShippingExit(Order order) {
        if (order.getProduct().isSingleItem()
                || !hasAdjustment(order, InventoryChangeType.RESERVATION)
                || hasAdjustment(order, InventoryChangeType.RELEASE)) {
            return;
        }
        Inventory inventory = findInventory(order.getProduct());
        inventoryAdjustmentRepository.saveAndFlush(inventory.release(order));
        recordInventoryOutcome("RELEASE", inventory, inventory.getAvailableQuantity(), false, Instant.now());
        eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseAfterFailedPaymentApproval(Long orderId) {
        orderRepository.findStateChangeTargetById(orderId)
                .filter(order -> order.getStatus() == OrderStatus.CREATED && !order.getProduct().isSingleItem())
                .ifPresent(this::releaseForFailedPaymentApproval);
    }

    public void releaseForFailedPaymentApproval(Order order) {
        if (order.getStatus() != OrderStatus.CREATED) {
            return;
        }
        order.cancel();
        if (order.getProduct().isSingleItem()) {
            orderRepository.flush();
            recordProductOutcome("RESTORE", order.getProduct(), false, Instant.now());
        }
        releaseForPreShippingExit(order);
    }

    @Transactional
    public void commitForShipment(Order order) {
        if (order.getProduct().isSingleItem()
                || hasAdjustment(order, InventoryChangeType.SHIPMENT_COMMITMENT)) {
            return;
        }
        Inventory inventory = findInventory(order.getProduct());
        inventoryAdjustmentRepository.saveAndFlush(inventory.commitShipment(order));
        recordInventoryOutcome("SHIPMENT", inventory, inventory.getAvailableQuantity(),
                inventory.getAvailableQuantity() == 0, Instant.now());
    }

    public InventoryAdjustmentResponse adjust(
            Long memberId,
            Long storeId,
            Long productId,
            InventoryAdjustmentRequest request
    ) {
        try {
            InventoryAdjustmentResponse response = inventoryAdjustmentTransactionService.adjust(memberId, storeId, productId, request);
            eventPublisher.publishEvent(new DiscoveryInvalidationEvent());
            return response;
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException exception) {
            throw new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_CONFLICT, exception);
        }
    }

    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponse> history(
            Long memberId,
            Long storeId,
            Long productId,
            Integer page,
            Integer size
    ) {
        storeAccessService.requireOperator(memberId, storeId);
        inventoryRepository.findByProductIdAndProductStoreId(productId, storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? 20 : size;
        if (resolvedPage < 0 || resolvedSize < 1 || resolvedSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return inventoryAdjustmentRepository
                .findHistoryByProductId(productId, PageRequest.of(resolvedPage, resolvedSize))
                .map(InventoryAdjustmentResponse::from);
    }

    private Inventory findInventory(Product product) {
        return inventoryRepository.findByProductIdAndProductStoreId(product.getId(), product.getStore().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE));
    }

    private boolean hasAdjustment(Order order, InventoryChangeType changeType) {
        return inventoryAdjustmentRepository.existsByOrderIdAndChangeType(order.getId(), changeType);
    }

    private void recordInventoryOutcome(
            String action,
            Inventory inventory,
            Integer availableQuantity,
            boolean soldOut,
            Instant occurredAt
    ) {
        if (inventoryOutcomeEventFactory == null) {
            return;
        }
        Product product = inventory.getProduct();
        operationalEventRecorder.record(inventoryOutcomeEventFactory.outcome(
                action, product.getId(), product.getStore().getId(), product.getSalesPolicy().name(),
                availableQuantity, soldOut, inventory.getVersion(), occurredAt));
    }

    private void recordProductOutcome(String action, Product product, boolean soldOut, Instant occurredAt) {
        if (inventoryOutcomeEventFactory == null) {
            return;
        }
        operationalEventRecorder.record(inventoryOutcomeEventFactory.outcome(
                action, product.getId(), product.getStore().getId(), product.getSalesPolicy().name(),
                null, soldOut, product.getVersion(), occurredAt));
    }
}
