package com.entry.poll.dto;

import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.util.UUID;

/** {@code myVote}: true = Yes, false = No, null = not voted. */
public record PollDto(
        UUID id,
        ResidentDto author,
        String question,
        Instant expiresAt,
        boolean expired,
        PollResults results,
        Boolean myVote,
        Instant createdAt) {
}
