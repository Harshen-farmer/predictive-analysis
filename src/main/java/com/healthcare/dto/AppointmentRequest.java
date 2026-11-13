package com.healthcare.dto;

import com.healthcare.model.AppointmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentRequest(
        @NotNull @Positive Long patientId,
        @NotNull @Positive Long doctorId,
        @NotNull LocalDate date,
        LocalTime slotTime,
        @NotNull AppointmentType type) {
}
