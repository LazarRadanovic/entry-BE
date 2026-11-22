package com.entry.feed.dto;

import com.entry.file.dto.FileDto;
import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostDto(
        UUID id,
        ResidentDto author,
        String text,
        List<FileDto> images,
        int likeCount,
        int commentCount,
        boolean likedByMe,
        Instant createdAt) {
}
