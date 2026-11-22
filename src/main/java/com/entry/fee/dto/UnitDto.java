package com.entry.fee.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** An apartment. {@code residents}: app users living there (empty for admin-added apartments). */
public record UnitDto(UUID id, String floor, String apartment, BigDecimal areaM2, List<String> residents,
        boolean mine) {
}
