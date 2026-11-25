package com.entry.security;

import com.entry.user.Role;
import com.entry.user.User;
import com.entry.user.UserStatus;
import java.util.UUID;

/**
 * The authenticated caller, rebuilt from the database on every request so that
 * approvals, removals and role changes take effect immediately (not after the JWT expires).
 * Inject it in controllers with {@code @AuthenticationPrincipal AuthUser me}.
 */
public record AuthUser(UUID id, UUID entranceId, Role role, UserStatus status, boolean entranceReadOnly) {

    /** {@code entranceReadOnly}: trial over without a subscription (see BillingPolicy): reading only. */
    public static AuthUser from(User user, boolean entranceReadOnly) {
        return new AuthUser(user.getId(), user.getEntrance().getId(), user.getRole(), user.getStatus(),
                entranceReadOnly);
    }

    public boolean isAdmin() {
        return role == Role.ENTRANCE_ADMIN;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
