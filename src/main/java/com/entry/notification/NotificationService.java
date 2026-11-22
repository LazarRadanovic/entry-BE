package com.entry.notification;

import com.entry.common.exception.ApiException;
import com.entry.common.web.Cursor;
import com.entry.common.web.CursorPage;
import com.entry.notification.dto.NotificationDto;
import com.entry.notification.dto.NotificationSettingsDto;
import com.entry.security.AuthUser;
import com.entry.user.UserMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_PAGE = 30;
    private static final int MAX_PAGE = 100;

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settings;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public CursorPage<NotificationDto> list(AuthUser me, String cursor, Integer size) {
        Cursor c = Cursor.decode(cursor);
        int pageSize = Cursor.pageSize(size, DEFAULT_PAGE, MAX_PAGE);
        List<Notification> rows = notifications.findPage(me.id(), c.createdAt(), c.id(),
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<Notification> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore ? new Cursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode() : null;
        return new CursorPage<>(page.stream().map(this::toDto).toList(), next);
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthUser me) {
        return notifications.countByUserIdAndReadAtIsNull(me.id());
    }

    @Transactional
    public NotificationDto markRead(AuthUser me, UUID id) {
        Notification n = notifications.findByIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        return toDto(n);
    }

    @Transactional
    public void markAllRead(AuthUser me) {
        notifications.markAllRead(me.id(), Instant.now());
    }

    @Transactional(readOnly = true)
    public NotificationSettingsDto getSettings(AuthUser me) {
        return toDto(settingsFor(me.id()));
    }

    @Transactional
    public NotificationSettingsDto updateSettings(AuthUser me, NotificationSettingsDto req) {
        NotificationSettings s = settings.findById(me.id()).orElseGet(() -> new NotificationSettings(me.id()));
        s.setPushEnabled(req.pushEnabled());
        s.setNewMessages(req.newMessages());
        s.setNewPolls(req.newPolls());
        s.setNewPosts(req.newPosts());
        return toDto(settings.save(s));
    }

    /** Defaults (all on) when the user never changed anything. */
    NotificationSettings settingsFor(UUID userId) {
        return settings.findById(userId).orElseGet(() -> new NotificationSettings(userId));
    }

    /** Requires an open session if the actor is a lazy proxy. */
    NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(),
                n.getActor() == null ? null : userMapper.toResident(n.getActor()),
                n.getTargetId(), n.getPreview(), n.getAggregateCount(), n.getReadAt() != null, n.getCreatedAt());
    }

    private static NotificationSettingsDto toDto(NotificationSettings s) {
        return new NotificationSettingsDto(s.isPushEnabled(), s.isNewMessages(), s.isNewPolls(), s.isNewPosts());
    }
}
