package com.entry.feed.event;

import com.entry.feed.dto.CommentDto;
import java.util.UUID;

public record CommentCreatedEvent(UUID entranceId, UUID postAuthorId, CommentDto comment) {
}
