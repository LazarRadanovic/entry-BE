package com.entry.fee.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MarkPaymentRequest(
        @NotNull UUID unitId,
        @Min(2000) @Max(2100) int year,
        @Min(1) @Max(12) int month,
        @NotNull Boolean paid) {
}
