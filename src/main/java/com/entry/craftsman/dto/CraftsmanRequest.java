package com.entry.craftsman.dto;

import com.entry.craftsman.Trade;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code photoFileId}: an image uploaded by the caller via POST /api/files, or null for no photo. */
public record CraftsmanRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Trade trade,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[+0-9 ()/-]+", message = "Invalid phone number") String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 255) @Pattern(regexp = "^$|https?://.+", message = "Website must start with http:// or https://")
        String website,
        @Size(max = 1000) String description,
        UUID photoFileId) {
}
