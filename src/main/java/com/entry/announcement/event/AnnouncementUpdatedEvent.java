package com.entry.announcement.event;

import com.entry.announcement.dto.AnnouncementDto;
import java.util.UUID;

public record AnnouncementUpdatedEvent(UUID entranceId, AnnouncementDto announcement) {
}
