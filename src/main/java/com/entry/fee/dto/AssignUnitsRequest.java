package com.entry.fee.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AssignUnitsRequest(@NotNull Boolean appliesToAll, @Size(max = 1000) List<UUID> unitIds) {
}
