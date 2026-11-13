package com.healthcare.dto;

import java.time.LocalTime;

public record DoctorResponse(
        Long id,
        String name,
        Long departmentId,
        String departmentName,
        LocalTime workStart,
        LocalTime workEnd,
        int slotMinutes,
        boolean onDuty) {
}
