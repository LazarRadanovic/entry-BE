package com.entry.lostfound.dto;

import com.entry.lostfound.LostFoundCategory;
import com.entry.lostfound.LostFoundKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code photoFileId}: an image uploaded by the caller via POST /api/files, or null for no photo. */
public record LostFoundRequest(
        @NotNull LostFoundKind kind,
        @NotNull LostFoundCategory category,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        @Size(max = 100) String location,
        UUID photoFileId) {
}
