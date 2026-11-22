package com.entry.chat;

import com.entry.chat.dto.ChatMessageDto;
import com.entry.chat.dto.SendMessageRequest;
import com.entry.chat.event.ChatMessageCreatedEvent;
import com.entry.common.web.Cursor;
import com.entry.common.web.CursorPage;
import com.entry.file.FileService;
import com.entry.file.StoredFile;
import com.entry.security.AuthUser;
import com.entry.user.UserMapper;
import com.entry.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_PAGE = 50;
    private static final int MAX_PAGE = 100;

    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final FileService fileService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    /** Newest first; the client reverses for display and loads older pages while scrolling up. */
    @Transactional(readOnly = true)
    public CursorPage<ChatMessageDto> history(AuthUser me, String cursor, Integer size) {
        Cursor c = Cursor.decode(cursor);
        int pageSize = Cursor.pageSize(size, DEFAULT_PAGE, MAX_PAGE);
        List<ChatMessage> rows = messages.findPage(me.entranceId(), c.createdAt(), c.id(),
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<ChatMessage> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore ? new Cursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode() : null;
        return new CursorPage<>(page.stream().map(this::toDto).toList(), next);
    }

    @Transactional
    public ChatMessageDto send(AuthUser me, SendMessageRequest req) {
        ChatMessage m = new ChatMessage();
        m.setEntranceId(me.entranceId());
        m.setSender(users.getReferenceById(me.id()));
        m.setText(req.text() == null || req.text().isBlank() ? null : req.text().trim());
        if (req.imageId() != null) {
            StoredFile image = fileService.requireOwnFiles(me, List.of(req.imageId())).getFirst();
            m.setImage(image);
        }
        m = messages.saveAndFlush(m);

        ChatMessageDto dto = toDto(m);
        events.publishEvent(new ChatMessageCreatedEvent(me.entranceId(), dto));
        return dto;
    }

    private ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(m.getId(), userMapper.toResident(m.getSender()), m.getText(),
                m.getImage() == null ? null : fileService.toDto(m.getImage()), m.getCreatedAt());
    }
}
