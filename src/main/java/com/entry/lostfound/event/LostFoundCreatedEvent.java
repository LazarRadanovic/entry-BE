package com.entry.lostfound.event;

import com.entry.lostfound.dto.LostFoundDto;
import java.util.UUID;

public record LostFoundCreatedEvent(UUID entranceId, LostFoundDto item) {
}
