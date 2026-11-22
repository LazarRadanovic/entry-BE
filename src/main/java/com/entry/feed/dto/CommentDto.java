package com.entry.feed.dto;

import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.util.UUID;

public record CommentDto(UUID id, UUID postId, ResidentDto author, String text, Instant createdAt) {
}
