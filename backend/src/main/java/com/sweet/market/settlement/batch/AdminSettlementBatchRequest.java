package com.sweet.market.settlement.batch;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
