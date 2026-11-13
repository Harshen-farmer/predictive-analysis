package com.healthcare.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) Integer bedCapacity,
        @NotNull @DecimalMin("0.0") Double averageStay,
        @NotNull @Min(1) Integer patientsPerDoctor) {
}
