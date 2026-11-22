package com.entry.craftsman.dto;

import java.util.UUID;

public record RecommendResponse(UUID craftsmanId, long recommendations, boolean recommendedByMe) {
}
