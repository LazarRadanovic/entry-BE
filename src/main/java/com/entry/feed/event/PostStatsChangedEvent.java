package com.entry.feed.event;

import java.util.UUID;

/** Like or comment counters changed (fires for likes, unlikes, new and deleted comments). */
public record PostStatsChangedEvent(UUID entranceId, UUID postId, int likeCount, int commentCount) {
}
