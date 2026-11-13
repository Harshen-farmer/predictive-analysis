package com.healthcare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffCreateRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
