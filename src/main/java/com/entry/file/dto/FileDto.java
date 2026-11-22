package com.entry.file.dto;

import java.util.UUID;

/** URLs are relative to the API origin and already signed; use them directly in {@code <img src>}. */
public record FileDto(UUID id, String url, String thumbnailUrl, int width, int height) {
}
