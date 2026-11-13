package com.healthcare.dto;

public record DepartmentOccupancyResponse(
        Long departmentId,
        String departmentName,
        long occupied,
        long available,
        long maintenance) {
}
