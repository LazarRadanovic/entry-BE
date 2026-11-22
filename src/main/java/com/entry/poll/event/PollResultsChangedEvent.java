package com.entry.poll.event;

import com.entry.poll.dto.PollResults;
import java.util.UUID;

public record PollResultsChangedEvent(UUID entranceId, PollResults results) {
}
