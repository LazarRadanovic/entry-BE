package com.entry.user;

public enum UserStatus {
    /** Joined with an invite code, waiting for an entrance admin. */
    PENDING,
    ACTIVE,
    /** Rejected or removed by an entrance admin. */
    REJECTED
}
