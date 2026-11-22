package com.entry.fee.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One apartment's line for one cost. {@code share}: what it owes per period (null if area is missing).
 * {@code paid}: 12 entries (January first) for monthly costs, 1 for one-time costs; when the admin marked it
 * paid, or null.
 */
public record CostRowDto(UUID unitId, String floor, String apartment, BigDecimal areaM2, List<String> residents,
        boolean mine, BigDecimal share, List<Instant> paid) {
}
