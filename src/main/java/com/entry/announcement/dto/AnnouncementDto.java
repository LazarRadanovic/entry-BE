package com.entry.announcement.dto;

import com.entry.announcement.AnnouncementKind;
import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** {@code author} carries floor and apartment. {@code ongoing}: today is between the start and end date. */
public record AnnouncementDto(
        UUID id,
        ResidentDto author,
        AnnouncementKind kind,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime fromTime,
        LocalTime toTime,
        String note,
        boolean ongoing,
        Instant createdAt) {
}
