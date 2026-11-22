package com.entry.poll.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Options are always Yes/No. {@code expiresAt} is optional (null = no expiry). */
public record CreatePollRequest(
        @NotBlank @Size(max = 300) String question,
        @Future(message = "Expiration must be in the future") Instant expiresAt) {
}
