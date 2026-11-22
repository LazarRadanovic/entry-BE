package com.entry.chat;

import com.entry.chat.dto.ChatMessageDto;
import com.entry.chat.dto.SendMessageRequest;
import com.entry.common.web.CursorPage;
import com.entry.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Messages are sent over REST (validation errors come back as normal HTTP responses) and delivered
 * to everyone in the entrance over STOMP on {@code /topic/entrance/{entranceId}/chat}.
 */
@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public CursorPage<ChatMessageDto> history(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return chatService.history(me, cursor, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageDto send(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SendMessageRequest request) {
        return chatService.send(me, request);
    }
}
