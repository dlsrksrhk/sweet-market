package com.sweet.market.settlement.query;

import com.sweet.market.settlement.api.SettlementResponse;
import com.sweet.market.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;

    public SettlementQueryService(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> findMine(Long sellerId) {
        return settlementRepository.findBySellerIdOrderByIdDesc(sellerId).stream()
                .map(SettlementResponse::from)
                .toList();
    }
}
