package com.healthcare.dto;

import java.util.List;

public record AllocationReport(
        List<DepartmentAllocation> departments,
        List<TransferSuggestion> transferSuggestions) {
}
