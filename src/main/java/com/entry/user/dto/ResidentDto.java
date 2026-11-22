package com.entry.user.dto;

import com.entry.user.Role;
import com.entry.user.User;
import java.util.UUID;

/** What neighbours see about each other (no email). */
public record ResidentDto(UUID id, String firstName, String lastName, String floor, String apartment, Role role,
        String avatarUrl) {

    public static ResidentDto from(User u, String avatarUrl) {
        return new ResidentDto(u.getId(), u.getFirstName(), u.getLastName(), u.getFloor(), u.getApartment(),
                u.getRole(), avatarUrl);
    }
}
