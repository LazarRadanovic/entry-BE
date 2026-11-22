package com.entry.fee.dto;

import com.entry.fee.CostRecurrence;
import com.entry.fee.CostType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Create or fully update a cost. {@code unitIds} is used only when {@code appliesToAll} is false. */
public record CostRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull CostType type,
        @NotNull @DecimalMin("0") @DecimalMax("100000000") BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "Use a 3-letter currency code, e.g. RSD or EUR") String currency,
        @NotNull CostRecurrence recurrence,
        LocalDate dueDate,
        @NotNull Boolean appliesToAll,
        @Size(max = 1000) List<UUID> unitIds) {
}
