package com.entry.chat.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code imageId} comes from {@code POST /api/files}. A message needs text, an image, or both. */
public record SendMessageRequest(@Size(max = 2000) String text, UUID imageId) {

    @AssertTrue(message = "A message needs text or an image")
    public boolean isNotEmpty() {
        return (text != null && !text.isBlank()) || imageId != null;
    }
}
