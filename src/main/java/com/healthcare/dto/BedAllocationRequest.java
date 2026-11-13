package com.healthcare.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BedAllocationRequest(
        @NotNull @Positive Long patientId,
        @NotNull @Positive Long departmentId) {
}
