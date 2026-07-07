package com.sweet.market.refund.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRejectRequest(
        @NotBlank
        @Size(min = 5, max = 500)
        String rejectReason
) {
}
