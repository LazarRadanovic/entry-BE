package com.entry.user.dto;

import com.entry.user.Role;
import com.entry.user.User;
import com.entry.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** Admin view of a member or join request. */
public record MemberDto(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String floor,
        String apartment,
        Role role,
        UserStatus status,
        String avatarUrl,
        Instant createdAt) {

    public static MemberDto from(User u, String avatarUrl) {
        return new MemberDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getFloor(),
                u.getApartment(), u.getRole(), u.getStatus(), avatarUrl, u.getCreatedAt());
    }
}
