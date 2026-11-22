package com.entry.feed.event;

import java.util.UUID;

public record PostDeletedEvent(UUID entranceId, UUID postId) {
}
