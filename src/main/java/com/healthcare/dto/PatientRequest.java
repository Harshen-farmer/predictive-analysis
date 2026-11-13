package com.healthcare.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(0) @Max(120) Integer age,
        @NotBlank @Size(max = 30) String gender,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String phone) {
}
