package com.entry.common.web;

import com.entry.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor: position {@code (createdAt, id)} of the last item already returned.
 * The "start" cursor sorts after everything, so queries need no null handling.
 */
public record Cursor(Instant createdAt, UUID id) {

    private static final Cursor START = new Cursor(Instant.parse("9999-12-31T00:00:00Z"),
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));

    public static Cursor decode(String value) {
        if (value == null || value.isBlank()) {
            return START;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("_");
            return new Cursor(Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                    UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("INVALID_CURSOR", "Invalid cursor");
        }
    }

    public String encode() {
        String raw = createdAt.getEpochSecond() + "_" + createdAt.getNano() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Clamp a client-supplied page size. */
    public static int pageSize(Integer requested, int defaultSize, int max) {
        if (requested == null || requested < 1) {
            return defaultSize;
        }
        return Math.min(requested, max);
    }
}
