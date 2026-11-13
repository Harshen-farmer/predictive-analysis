package com.healthcare.dto;

import com.healthcare.model.UserRole;

public record StaffAccountResponse(Long id, String username, UserRole role, boolean enabled) {
}
