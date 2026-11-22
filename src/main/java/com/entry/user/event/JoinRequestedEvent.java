package com.entry.user.event;

import java.util.UUID;

/** Someone registered with an invite code and waits for approval. */
public record JoinRequestedEvent(UUID entranceId, UUID userId) {
}
