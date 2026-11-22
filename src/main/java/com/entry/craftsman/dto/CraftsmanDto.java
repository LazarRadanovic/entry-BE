package com.entry.craftsman.dto;

import com.entry.craftsman.Trade;
import com.entry.file.dto.FileDto;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code addedBy}: name of the neighbour who added it (null if they left). {@code canEdit}: the caller is that
 * neighbour or an entrance admin.
 */
public record CraftsmanDto(UUID id, String name, Trade trade, String phone, String email, String website,
        String description, FileDto photo, String addedBy, boolean canEdit, long recommendations,
        boolean recommendedByMe, Instant createdAt) {
}
