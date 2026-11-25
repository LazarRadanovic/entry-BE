package com.entry.announcement.event;

import java.util.UUID;

public record AnnouncementDeletedEvent(UUID entranceId, UUID announcementId) {
}
