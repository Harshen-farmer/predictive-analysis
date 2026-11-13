package com.healthcare.dto;

import java.time.LocalDate;

public record BedAllocationResponse(
        Long id,
        Long patientId,
        String patientName,
        Long bedId,
        String bedNumber,
        Long departmentId,
        String departmentName,
        LocalDate admitDate,
        LocalDate dischargeDate) {
}
