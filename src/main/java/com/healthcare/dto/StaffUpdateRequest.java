package com.healthcare.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record StaffUpdateRequest(
        Boolean enabled,
        @Size(min = 8, max = 72) String password) {

    @AssertTrue(message = "Provide enabled and/or a new password")
    public boolean hasUpdate() {
        return enabled != null || (password != null && !password.isBlank());
    }
}
