package com.healthcare.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AllocationThresholdsRequest(
        @NotNull @DecimalMin("0.0") Double warningPercent,
        @NotNull @DecimalMin("0.0") Double shortagePercent) {
}
