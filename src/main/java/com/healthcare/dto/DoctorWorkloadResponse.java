package com.healthcare.dto;

public record DoctorWorkloadResponse(
        Long doctorId,
        String doctorName,
        Long departmentId,
        String departmentName,
        long patientsToday,
        int patientLimit) {
}
