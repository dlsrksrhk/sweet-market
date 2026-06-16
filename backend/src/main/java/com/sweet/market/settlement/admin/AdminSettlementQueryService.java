package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
public class AdminSettlementQueryService {

    private static final LocalDateTime MIN_SETTLED_AT = LocalDateTime.of(1, 1, 1, 0, 0);
    private static final LocalDateTime MAX_SETTLED_AT = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999);

    private final SettlementRepository settlementRepository;

    public AdminSettlementQueryService(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminSettlementSummaryResponse> search(AdminSettlementSearchRequest request, Pageable pageable) {
        LocalDateTime settledFrom = request.settledFrom() == null ? MIN_SETTLED_AT : request.settledFrom();
        LocalDateTime settledTo = request.settledTo() == null ? MAX_SETTLED_AT : request.settledTo();

        return settlementRepository.searchAdminSettlements(
                request.orderId(),
                request.sellerId(),
                request.status(),
                settledFrom,
                settledTo,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminSettlementDetailResponse findDetail(Long settlementId) {
        return settlementRepository.findAdminSettlementDetail(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }
}
