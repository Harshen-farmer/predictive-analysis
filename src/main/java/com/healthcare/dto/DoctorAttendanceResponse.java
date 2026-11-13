package com.healthcare.dto;

import java.time.LocalDate;

public record DoctorAttendanceResponse(
        Long doctorId,
        String doctorName,
        String departmentName,
        LocalDate date,
        boolean present,
        boolean onDuty) {
}
