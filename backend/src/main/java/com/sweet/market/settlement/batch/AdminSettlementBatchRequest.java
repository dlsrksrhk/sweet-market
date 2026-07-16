package com.sweet.market.settlement.batch;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record AdminSettlementBatchRequest(
        @NotNull LocalDateTime confirmedBefore,
        @NotNull @Positive @Max(1000) Integer limit,
        @NotNull @Positive @Max(100) Integer chunkSize
) {

    @AssertTrue(message = "chunkSize must not be greater than limit")
    public boolean isChunkSizeNotGreaterThanLimit() {
        if (limit == null || chunkSize == null) {
            return true;
        }
        return chunkSize <= limit;
    }
}
