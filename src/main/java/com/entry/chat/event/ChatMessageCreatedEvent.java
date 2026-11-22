package com.entry.chat.event;

import com.entry.chat.dto.ChatMessageDto;
import java.util.UUID;

public record ChatMessageCreatedEvent(UUID entranceId, ChatMessageDto message) {
}
