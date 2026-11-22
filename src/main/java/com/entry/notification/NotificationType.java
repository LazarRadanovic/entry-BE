package com.entry.notification;

/** The frontend picks the icon and sentence by type (e.g. POST_LIKED: "Marko and 4 others liked your post"). */
public enum NotificationType {
    NEW_POST,
    NEW_POLL,
    NEW_COMMENT,
    POST_LIKED,
    JOIN_REQUEST,
    MEMBERSHIP_APPROVED,
    /** To admins: the free trial ends soon (preview = days left). */
    TRIAL_ENDING
}
