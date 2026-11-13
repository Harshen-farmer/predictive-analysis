package com.healthcare.dto;

import java.util.List;

public record DepartmentForecast(
        Long departmentId,
        String department,
        List<DailyForecast> history,
        List<DailyForecast> forecast) {
}
