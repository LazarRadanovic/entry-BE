package com.entry.user.dto;

import com.entry.entrance.dto.EntranceSummary;
import com.entry.user.Role;
import com.entry.user.User;
import com.entry.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** The current user's own view of themselves (includes email, status and entrance). */
public record UserDto(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String floor,
        String apartment,
        Role role,
        UserStatus status,
        String avatarUrl,
        EntranceSummary entrance,
        Instant createdAt) {

    /** Requires {@code user.getEntrance()} to be loaded (call inside a transaction). */
    public static UserDto from(User u, String avatarUrl) {
        return new UserDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getFloor(),
                u.getApartment(), u.getRole(), u.getStatus(), avatarUrl, EntranceSummary.from(u.getEntrance()),
                u.getCreatedAt());
    }
}
