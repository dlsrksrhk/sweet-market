package com.sweet.market.gateway.probe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProbeRequest(@NotBlank @Size(max = 100) String message) {}
