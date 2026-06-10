package com.sweet.market.settlement.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Component
public class SettlementItemWriter implements ItemWriter<Settlement> {

    private final SettlementRepository settlementRepository;

    public SettlementItemWriter(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    @Override
    public void write(Chunk<? extends Settlement> chunk) {
        for (Settlement settlement : chunk.getItems()) {
            write(settlement);
        }
    }

    private void write(Settlement settlement) {
        Long orderId = settlement.getOrder().getId();
        int inserted = settlementRepository.insertIfAbsent(
                orderId,
                settlement.getSeller().getId(),
                settlement.getAmount(),
                settlement.getStatus().name(),
                settlement.getSettledAt()
        );

        if (inserted == 0) {
            throw new SettlementBatchSkippableException("Settlement already exists for order: " + orderId);
        }
    }
}
