package com.healthcare.dto;

import com.healthcare.model.TransferResourceType;
import com.healthcare.model.TransferStatus;

public record TransferSuggestion(
        Long id,
        Long fromDepartmentId,
        String fromDepartment,
        Long toDepartmentId,
        String toDepartment,
        TransferResourceType resourceType,
        int quantity,
        String reason,
        TransferStatus status,
        String decidedBy) {
}
