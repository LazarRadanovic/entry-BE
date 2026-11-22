package com.entry.feed.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** {@code imageIds} come from {@code POST /api/files}. A post needs text, images, or both. */
public record CreatePostRequest(
        @Size(max = 2000) String text,
        @Size(max = 10, message = "At most 10 images per post") List<@NotNull UUID> imageIds) {

    @AssertTrue(message = "A post needs text or at least one image")
    public boolean isNotEmpty() {
        return (text != null && !text.isBlank()) || (imageIds != null && !imageIds.isEmpty());
    }

    public List<UUID> imageIdsOrEmpty() {
        return imageIds == null ? List.of() : imageIds;
    }
}
