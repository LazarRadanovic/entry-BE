package com.entry.poll.event;

import java.util.UUID;

public record PollDeletedEvent(UUID entranceId, UUID pollId) {
}
