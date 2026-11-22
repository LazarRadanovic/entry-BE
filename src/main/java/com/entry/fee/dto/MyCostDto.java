package com.entry.fee.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A cost that applies to the caller's apartment, with what it owes and what was paid (see CostRowDto.paid). */
public record MyCostDto(CostDto cost, BigDecimal share, List<Instant> paid) {
}
