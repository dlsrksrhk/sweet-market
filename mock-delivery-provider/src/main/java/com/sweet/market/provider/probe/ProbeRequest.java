package com.sweet.market.provider.probe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProbeRequest(@NotBlank @Size(max = 100) String message) {}
