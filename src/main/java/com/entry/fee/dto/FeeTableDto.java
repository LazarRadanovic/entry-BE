package com.entry.fee.dto;

import java.math.BigDecimal;
import java.util.List;

/** The cleaning-fee table of one year. {@code amount} is null until the admin sets it. */
public record FeeTableDto(int year, BigDecimal amount, String currency, List<FeeRowDto> rows) {
}
