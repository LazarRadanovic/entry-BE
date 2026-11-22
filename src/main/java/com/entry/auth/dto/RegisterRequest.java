package com.entry.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Exactly one of {@code inviteCode} (join an existing entrance, becomes PENDING RESIDENT)
 * or {@code newEntrance} (create one, becomes ACTIVE ENTRANCE_ADMIN) must be provided.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Size(max = 10) String floor,
        @NotBlank @Size(max = 10) String apartment,
        @Size(max = 16) String inviteCode,
        @Valid NewEntranceRequest newEntrance) {

    @AssertTrue(message = "Provide either an invite code or a new entrance (not both)")
    public boolean isEntranceChoiceValid() {
        return hasInviteCode() ^ (newEntrance != null);
    }

    public boolean hasInviteCode() {
        return inviteCode != null && !inviteCode.isBlank();
    }
}
