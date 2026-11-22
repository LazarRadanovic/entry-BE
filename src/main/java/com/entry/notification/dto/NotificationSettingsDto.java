package com.entry.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingsDto(
        @NotNull Boolean pushEnabled,
        @NotNull Boolean newMessages,
        @NotNull Boolean newPolls,
        @NotNull Boolean newPosts) {
}
