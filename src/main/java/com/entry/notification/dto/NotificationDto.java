package com.entry.notification.dto;

import com.entry.notification.NotificationType;
import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code targetId}: post id (NEW_POST, NEW_COMMENT, POST_LIKED), poll id (NEW_POLL),
 * user id (JOIN_REQUEST), entrance id (MEMBERSHIP_APPROVED). {@code actor} can be null if that user was deleted.
 */
public record NotificationDto(
        UUID id,
        NotificationType type,
        ResidentDto actor,
        UUID targetId,
        String preview,
        int count,
        boolean read,
        Instant createdAt) {
}
