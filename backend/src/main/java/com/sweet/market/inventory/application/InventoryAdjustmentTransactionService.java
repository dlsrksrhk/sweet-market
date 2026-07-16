package com.sweet.market.inventory.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.inventory.domain.Inventory;
import com.sweet.market.inventory.domain.InventoryAdjustment;
import com.sweet.market.inventory.domain.InventoryDomainError;
import com.sweet.market.inventory.repository.InventoryAdjustmentRepository;
import com.sweet.market.inventory.repository.InventoryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.store.application.StoreAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAdjustmentTransactionService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final MemberRepository memberRepository;
    private final StoreAccessService storeAccessService;

    public InventoryAdjustmentTransactionService(
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
        Member actor = memberRepository.getReferenceById(memberId);
        InventoryAdjustment adjustment;
        try {
            adjustment = inventory.adjust(
                    request.totalQuantity(),
                    request.reason(),
                    request.referenceNote(),
                    actor
            );
        } catch (DomainException exception) {
            ErrorCode errorCode = switch ((InventoryDomainError) exception.error()) {
                case TOTAL_BELOW_RESERVED_QUANTITY -> ErrorCode.INVENTORY_ADJUSTMENT_CONFLICT;
                default -> ErrorCode.VALIDATION_ERROR;
            };
            throw new BusinessException(errorCode, exception);
        }
        inventoryRepository.saveAndFlush(inventory);
        return InventoryAdjustmentResponse.from(inventoryAdjustmentRepository.save(adjustment));
    }
}
