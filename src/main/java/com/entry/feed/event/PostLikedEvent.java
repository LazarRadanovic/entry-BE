package com.entry.feed.event;

import java.util.UUID;

public record PostLikedEvent(UUID entranceId, UUID postId, UUID postAuthorId, UUID likerId) {
}
