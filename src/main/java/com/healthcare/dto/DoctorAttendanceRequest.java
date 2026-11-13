package com.healthcare.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DoctorAttendanceRequest(
        @NotNull LocalDate date,
        @NotNull Boolean present) {
}
