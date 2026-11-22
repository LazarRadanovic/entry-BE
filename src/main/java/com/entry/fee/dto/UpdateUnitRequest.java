package com.entry.fee.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** {@code areaM2} null clears it. */
public record UpdateUnitRequest(@DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") BigDecimal areaM2) {
}
