package com.entry.fee.dto;

import java.util.List;
import java.util.UUID;

/** The caller's apartment and its costs. {@code unitId} is null if the caller has no apartment row yet. */
public record MyCostsDto(int year, UUID unitId, String floor, String apartment, List<MyCostDto> costs) {
}
