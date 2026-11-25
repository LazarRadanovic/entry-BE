package com.entry.lostfound.event;

import com.entry.lostfound.dto.LostFoundDto;
import java.util.UUID;

/** Edited, resolved, reopened or someone responded. */
public record LostFoundUpdatedEvent(UUID entranceId, LostFoundDto item) {
}
