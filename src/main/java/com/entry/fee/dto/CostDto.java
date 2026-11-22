package com.entry.fee.dto;

import com.entry.fee.CostRecurrence;
import com.entry.fee.CostType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code unitIds}: assigned apartments (empty when {@code appliesToAll}).
 * {@code missingArea}: a BY_AREA cost has an assigned apartment without area, so shares can't be computed.
 */
public record CostDto(UUID id, String name, String description, CostType type, BigDecimal amount, String currency,
        CostRecurrence recurrence, boolean appliesToAll, List<UUID> unitIds, LocalDate dueDate,
        boolean missingArea) {
}
