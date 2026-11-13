package com.healthcare.dto;

import com.healthcare.model.AppointmentStatus;
import com.healthcare.model.AppointmentType;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentResponse(
        Long id,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        Long departmentId,
        LocalDate date,
        LocalTime slotTime,
        int tokenNumber,
        AppointmentStatus status,
        AppointmentType type) {
}
