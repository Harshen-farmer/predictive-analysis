package com.healthcare.controller;

import com.healthcare.dto.DepartmentForecast;
import com.healthcare.dto.DepartmentLoadToday;
import com.healthcare.service.PredictionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/admin/predictions")
    public List<DepartmentForecast> allDepartments() {
        return predictionService.forecastAll();
    }

    @GetMapping("/admin/predictions/{departmentId}")
    public DepartmentForecast department(@PathVariable Long departmentId) {
        return predictionService.forecast(departmentId);
    }

    @GetMapping("/staff/load-today")
    public List<DepartmentLoadToday> loadToday() {
        return predictionService.loadToday();
    }
}
