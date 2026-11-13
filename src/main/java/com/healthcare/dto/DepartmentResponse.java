package com.healthcare.dto;

public record DepartmentResponse(
        Long id,
        String name,
        int bedCapacity,
        double averageStay,
        int patientsPerDoctor) {
}
