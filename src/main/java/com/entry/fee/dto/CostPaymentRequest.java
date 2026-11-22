package com.entry.fee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** {@code period}: "YYYY-MM" for monthly costs, "ONCE" for one-time costs. */
public record CostPaymentRequest(
        @NotNull UUID costId,
        @NotNull UUID unitId,
        @NotNull @Pattern(regexp = "ONCE|\\d{4}-(0[1-9]|1[0-2])", message = "Use YYYY-MM or ONCE") String period,
        @NotNull Boolean paid) {
}
