package com.entry.feed.event;

import com.entry.feed.dto.PostDto;
import java.util.UUID;

public record PostCreatedEvent(UUID entranceId, PostDto post) {
}
