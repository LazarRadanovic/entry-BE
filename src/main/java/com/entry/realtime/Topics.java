package com.entry.realtime;

import java.util.UUID;

/**
 * STOMP destinations. Clients subscribe to:
 * <pre>
 *   /topic/entrance/{entranceId}/feed       {type: POST_CREATED | POST_DELETED | POST_STATS | COMMENT_CREATED, payload}
 *   /topic/entrance/{entranceId}/chat       {type: MESSAGE_CREATED, payload: ChatMessageDto}
 *   /topic/entrance/{entranceId}/polls      {type: POLL_CREATED | POLL_RESULTS | POLL_DELETED, payload}
 *   /topic/entrance/{entranceId}/announcements {type: ANNOUNCEMENT_CREATED | ANNOUNCEMENT_UPDATED | ANNOUNCEMENT_DELETED, payload}
 *   /topic/entrance/{entranceId}/lost-found {type: LOST_FOUND_CREATED | LOST_FOUND_UPDATED | LOST_FOUND_DELETED, payload}
 *   /topic/entrance/{entranceId}/presence   {type: PRESENCE, payload: {userId, online, onlineUserIds}}
 *   /topic/entrance/{entranceId}/fees       {type: FEES_UPDATED, payload: {year}}
 *   /user/queue/notifications               {type: NOTIFICATION, payload: NotificationDto}
 * </pre>
 */
public final class Topics {

    public static final String USER_NOTIFICATIONS = "/queue/notifications";

    private Topics() {
    }

    public static String entrancePrefix(UUID entranceId) {
        return "/topic/entrance/" + entranceId + "/";
    }

    public static String feed(UUID entranceId) {
        return entrancePrefix(entranceId) + "feed";
    }

    public static String chat(UUID entranceId) {
        return entrancePrefix(entranceId) + "chat";
    }

    public static String polls(UUID entranceId) {
        return entrancePrefix(entranceId) + "polls";
    }

    public static String announcements(UUID entranceId) {
        return entrancePrefix(entranceId) + "announcements";
    }

    public static String lostFound(UUID entranceId) {
        return entrancePrefix(entranceId) + "lost-found";
    }

    public static String presence(UUID entranceId) {
        return entrancePrefix(entranceId) + "presence";
    }
}
