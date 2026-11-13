package com.healthcare.dto;

import java.time.LocalDate;

public record BedOccupancyReportRow(
        LocalDate date,
        Long departmentId,
        String department,
        long occupied,
        long available,
        long maintenance) {
}
