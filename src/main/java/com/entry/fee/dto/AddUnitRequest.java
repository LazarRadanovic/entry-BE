package com.entry.fee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddUnitRequest(@NotBlank @Size(max = 10) String floor, @NotBlank @Size(max = 10) String apartment) {
}
