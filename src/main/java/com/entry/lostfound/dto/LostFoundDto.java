package com.entry.lostfound.dto;

import com.entry.file.dto.FileDto;
import com.entry.lostfound.LostFoundCategory;
import com.entry.lostfound.LostFoundKind;
import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code responders}: neighbours who said "it's mine" (FOUND) or "I found it" (LOST), oldest first.
 * {@code resolvedAt}: null while the item is still open.
 */
public record LostFoundDto(
        UUID id,
        ResidentDto author,
        LostFoundKind kind,
        LostFoundCategory category,
        String title,
        String description,
        String location,
        FileDto photo,
        List<ResidentDto> responders,
        Instant resolvedAt,
        Instant createdAt) {
}
