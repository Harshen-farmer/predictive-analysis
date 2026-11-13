package com.healthcare.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record DoctorRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive Long departmentId,
        @NotNull LocalTime workStart,
        @NotNull LocalTime workEnd,
        @NotNull @Min(5) @Max(60) Integer slotMinutes,
        @NotNull Boolean onDuty) {

    @AssertTrue(message = "workStart must be before workEnd")
    public boolean isWorkHoursValid() {
        return workStart == null || workEnd == null || workStart.isBefore(workEnd);
    }
}
