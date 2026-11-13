package com.healthcare.dto;

import com.healthcare.model.BedStatus;

public record BedResponse(Long id, String bedNumber, Long departmentId, String departmentName, BedStatus status) {
}
