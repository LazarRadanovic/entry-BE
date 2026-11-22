package com.entry.fee.dto;

import java.util.List;

/** One cost's payment table for a year, only the apartments it applies to. */
public record CostTableDto(int year, CostDto cost, List<CostRowDto> rows) {
}
