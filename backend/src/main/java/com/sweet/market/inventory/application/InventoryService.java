package com.sweet.market.inventory.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.domain.InventoryAdjustment;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.application.StoreAccessService;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final MemberRepository memberRepository;
    private final StoreAccessService storeAccessService;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            MemberRepository memberRepository,
            StoreAccessService storeAccessService
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.memberRepository = memberRepository;
        this.storeAccessService = storeAccessService;
    }

    public void initialize(Product product, int initialTotalQuantity, Long memberId) {
        Inventory inventory = inventoryRepository.save(Inventory.initialize(product, initialTotalQuantity));
        inventoryAdjustmentRepository.save(inventory.getInitializationAdjustment());
    }

    @Transactional
    public InventoryAdjustmentResponse adjust(
            Long memberId,
            Long storeId,
            Long productId,
            InventoryAdjustmentRequest request
    ) {
        storeAccessService.requireCatalogOperator(memberId, storeId);
        Inventory inventory = inventoryRepository.findForAdjustment(storeId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (request.totalQuantity() < inventory.getReservedQuantity()) {
            throw new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_CONFLICT);
        }
        Member actor = memberRepository.getReferenceById(memberId);
        InventoryAdjustment adjustment = inventory.adjust(
                request.totalQuantity(),
                request.reason(),
                request.referenceNote(),
                actor
        );
        try {
            inventoryRepository.saveAndFlush(inventory);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.INVENTORY_ADJUSTMENT_CONFLICT);
        }
        return InventoryAdjustmentResponse.from(inventoryAdjustmentRepository.save(adjustment));
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
}
