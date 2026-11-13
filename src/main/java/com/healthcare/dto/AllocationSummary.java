package com.healthcare.dto;

public record AllocationSummary(
        long patientsToday,
        long bedsInUse,
        long doctorsOnDuty,
        long departmentsInShortage) {
}
