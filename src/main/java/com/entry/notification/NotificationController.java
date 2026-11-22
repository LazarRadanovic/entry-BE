package com.entry.notification;

import com.entry.common.web.CursorPage;
import com.entry.notification.dto.NotificationDto;
import com.entry.notification.dto.NotificationSettingsDto;
import com.entry.notification.dto.UnreadCountDto;
import com.entry.security.AuthUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** New notifications also arrive live on {@code /user/queue/notifications} while connected. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public CursorPage<NotificationDto> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return service.list(me, cursor, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unreadCount(@AuthenticationPrincipal AuthUser me) {
        return new UnreadCountDto(service.unreadCount(me));
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.markRead(me, id);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthUser me) {
        service.markAllRead(me);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public NotificationSettingsDto settings(@AuthenticationPrincipal AuthUser me) {
        return service.getSettings(me);
    }

    @PutMapping("/settings")
    public NotificationSettingsDto updateSettings(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody NotificationSettingsDto request) {
        return service.updateSettings(me, request);
    }
}
