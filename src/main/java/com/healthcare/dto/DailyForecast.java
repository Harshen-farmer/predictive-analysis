package com.healthcare.dto;

import java.time.LocalDate;

public record DailyForecast(LocalDate date, int predictedPatients) {
}
