package com.entry.feed.dto;

import java.util.UUID;

public record LikeResponse(UUID postId, int likeCount, boolean likedByMe) {
}
