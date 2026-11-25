package com.entry.announcement.dto;

import com.entry.announcement.AnnouncementKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Dates are inclusive ({@code "2026-12-01"}). {@code fromTime}/{@code toTime} ({@code "08:00"}) are the daily hours:
 * give both or neither (= all day); {@code toTime} before {@code fromTime} means it runs past midnight.
 */
public record AnnouncementRequest(
        @NotNull AnnouncementKind kind,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalTime fromTime,
        LocalTime toTime,
        @Size(max = 500) String note) {
}
