package com.entry.announcement.event;

import com.entry.announcement.dto.AnnouncementDto;
import java.util.UUID;

public record AnnouncementCreatedEvent(UUID entranceId, AnnouncementDto announcement) {
}
