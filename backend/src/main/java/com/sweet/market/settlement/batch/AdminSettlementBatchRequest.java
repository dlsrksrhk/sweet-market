package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminSettlementBatchRequest(
        @NotNull LocalDateTime confirmedBefore,
        @NotNull @Positive Integer limit,
        @NotNull @Positive Integer chunkSize
) {

    @AssertTrue(message = "chunkSize must not be greater than limit")
    public boolean isChunkSizeNotGreaterThanLimit() {
        if (limit == null || chunkSize == null) {
            return true;
        }
        return chunkSize <= limit;
    }
}
