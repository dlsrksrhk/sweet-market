package com.sweet.market.refund.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequestCreateRequest(
        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
