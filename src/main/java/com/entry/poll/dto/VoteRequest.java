package com.entry.poll.dto;

import jakarta.validation.constraints.NotNull;

/** {@code true} = Yes, {@code false} = No. */
public record VoteRequest(@NotNull Boolean value) {
}
