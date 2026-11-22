package com.entry.fee.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record FeeSettingsRequest(
        @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "Use a 3-letter currency code, e.g. RSD or EUR") String currency) {
}
