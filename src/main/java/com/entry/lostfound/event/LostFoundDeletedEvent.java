package com.entry.lostfound.event;

import java.util.UUID;

public record LostFoundDeletedEvent(UUID entranceId, UUID itemId) {
}
