package com.healthcare.service;

import com.healthcare.dto.DailyForecast;
import com.healthcare.dto.DepartmentForecast;
import com.healthcare.dto.DepartmentLoadToday;
import com.healthcare.model.DailyLoad;
import com.healthcare.model.Department;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PredictionService {

    private static final int FORECAST_DAYS = 7;

    private final DailyLoadRepository dailyLoadRepository;
    private final DepartmentRepository departmentRepository;

    public PredictionService(
            DailyLoadRepository dailyLoadRepository,
            DepartmentRepository departmentRepository) {
        this.dailyLoadRepository = dailyLoadRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentForecast> forecastAll() {
        return departmentRepository.findAll().stream()
                .map(department -> forecast(department, LocalDate.now()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentForecast forecast(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + departmentId));
        return forecast(department, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<DepartmentLoadToday> loadToday() {
        LocalDate today = LocalDate.now();
        return departmentRepository.findAll().stream()
                .map(department -> new DepartmentLoadToday(
                        department.getId(),
                        department.getName(),
                        dailyLoadRepository.findByDepartment_IdAndDate(department.getId(), today)
                                .map(DailyLoad::getPatientCount)
                                .orElse(0)))
                .toList();
    }

    private DepartmentForecast forecast(Department department, LocalDate today) {
        List<DailyLoad> loads = dailyLoadRepository.findByDepartment_IdAndDateBetweenOrderByDate(
                department.getId(), today.minusDays(6), today);

        List<DailyForecast> history = loads.stream()
                .map(load -> new DailyForecast(load.getDate(), load.getPatientCount()))
                .toList();

        if (loads.isEmpty()) {
            return new DepartmentForecast(department.getId(), department.getName(), history, zeroForecast(today));
        }

        double overallAverage = loads.stream().mapToInt(DailyLoad::getPatientCount).average().orElse(0.0);
        Map<DayOfWeek, List<Integer>> weekdayCounts = new EnumMap<>(DayOfWeek.class);
        loads.forEach(load -> weekdayCounts.computeIfAbsent(load.getDate().getDayOfWeek(), ignored -> new ArrayList<>())
                .add(load.getPatientCount()));

        List<DailyForecast> forecast = new ArrayList<>(FORECAST_DAYS);
        for (int day = 1; day <= FORECAST_DAYS; day++) {
            LocalDate forecastDate = today.plusDays(day);
            List<Integer> weekdayValues = weekdayCounts.get(forecastDate.getDayOfWeek());
            double weekdayAverage = weekdayValues == null
                    ? overallAverage
                    : weekdayValues.stream().mapToInt(Integer::intValue).average().orElse(overallAverage);

            // Forecast = recent overall average * (weekday average / recent overall average).
            // If that weekday is absent, its factor is 1; when the history is all zero, the forecast is zero.
            double predicted = overallAverage == 0.0
                    ? 0.0
                    : overallAverage * (weekdayAverage / overallAverage);
            forecast.add(new DailyForecast(forecastDate, (int) Math.round(predicted)));
        }

        return new DepartmentForecast(department.getId(), department.getName(), history, forecast);
    }

    private List<DailyForecast> zeroForecast(LocalDate today) {
        List<DailyForecast> forecast = new ArrayList<>(FORECAST_DAYS);
        for (int day = 1; day <= FORECAST_DAYS; day++) {
            forecast.add(new DailyForecast(today.plusDays(day), 0));
        }
        return forecast;
    }
}
