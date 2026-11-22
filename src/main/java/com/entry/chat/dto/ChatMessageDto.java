package com.entry.chat.dto;

import com.entry.file.dto.FileDto;
import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.util.UUID;

/** {@code image} is null for text-only messages. */
public record ChatMessageDto(UUID id, ResidentDto sender, String text, FileDto image, Instant createdAt) {
}
