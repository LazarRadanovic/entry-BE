package com.entry.fee.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** {@code areaM2} is optional (needed only for costs split by area). */
public record AddUnitRequest(
        @NotBlank @Size(max = 10) String floor,
        @NotBlank @Size(max = 10) String apartment,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") BigDecimal areaM2) {
}
