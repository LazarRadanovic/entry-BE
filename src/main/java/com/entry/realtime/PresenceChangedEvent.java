package com.entry.realtime;

import java.util.UUID;

public record PresenceChangedEvent(UUID entranceId, UUID userId, boolean online) {
}
