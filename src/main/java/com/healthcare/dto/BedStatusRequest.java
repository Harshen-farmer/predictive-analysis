package com.healthcare.dto;

import com.healthcare.model.BedStatus;
import jakarta.validation.constraints.NotNull;

public record BedStatusRequest(@NotNull BedStatus status) {
}
