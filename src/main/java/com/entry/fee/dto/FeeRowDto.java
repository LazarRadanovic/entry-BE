package com.entry.fee.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One apartment. {@code paid} has 12 entries (January first): the time the admin marked it paid, or null.
 * {@code residents} are the app users living there (may be empty for admin-added apartments).
 */
public record FeeRowDto(UUID unitId, String floor, String apartment, List<String> residents, boolean mine,
        List<Instant> paid) {
}
