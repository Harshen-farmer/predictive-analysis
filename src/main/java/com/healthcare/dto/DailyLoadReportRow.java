package com.healthcare.dto;

import java.time.LocalDate;

public record DailyLoadReportRow(
        LocalDate date,
        Long departmentId,
        String department,
        int patientCount) {
}
