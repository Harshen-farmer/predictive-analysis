package com.healthcare.dto;

import com.healthcare.model.AppointmentStatus;
import com.healthcare.model.AppointmentType;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentReportRow(
        Long id,
        LocalDate date,
        LocalTime slotTime,
        int tokenNumber,
        String patient,
        String doctor,
        String department,
        AppointmentStatus status,
        AppointmentType type) {
}
