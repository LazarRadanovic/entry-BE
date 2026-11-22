package com.entry.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** e.g. name = "Entrance 3", address = "Belgrade Street 12". */
public record NewEntranceRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200) String address) {
}
