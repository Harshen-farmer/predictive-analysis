package com.healthcare.dto;

public record DepartmentAllocation(
        Long departmentId,
        String department,
        int predictedPatients,
        double bedsNeeded,
        long bedsAvailable,
        double bedsUtilization,
        int doctorsNeeded,
        long doctorsAvailable,
        double doctorsUtilization,
        String status) {
}
