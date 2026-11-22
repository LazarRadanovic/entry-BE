package com.entry.poll.event;

import com.entry.poll.dto.PollDto;
import java.util.UUID;

public record PollCreatedEvent(UUID entranceId, UUID authorId, PollDto poll) {
}
