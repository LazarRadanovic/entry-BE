package com.entry.realtime;

import com.entry.announcement.event.AnnouncementCreatedEvent;
import com.entry.announcement.event.AnnouncementDeletedEvent;
import com.entry.announcement.event.AnnouncementUpdatedEvent;
import com.entry.chat.event.ChatMessageCreatedEvent;
import com.entry.fee.CostsChangedEvent;
import com.entry.feed.event.CommentCreatedEvent;
import com.entry.feed.event.PostCreatedEvent;
import com.entry.feed.event.PostDeletedEvent;
import com.entry.feed.event.PostStatsChangedEvent;
import com.entry.lostfound.event.LostFoundCreatedEvent;
import com.entry.lostfound.event.LostFoundDeletedEvent;
import com.entry.lostfound.event.LostFoundUpdatedEvent;
import com.entry.poll.event.PollCreatedEvent;
import com.entry.poll.event.PollDeletedEvent;
import com.entry.poll.event.PollResultsChangedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Forwards domain events to STOMP topics, only after the transaction has committed. */
@Component
@RequiredArgsConstructor
public class RealtimeEventRelay {

    private final SimpMessagingTemplate broker;
    private final PresenceTracker presence;

    // ---- feed ----

    @TransactionalEventListener
    public void on(PostCreatedEvent e) {
        broker.convertAndSend(Topics.feed(e.entranceId()), new RealtimeEvent("POST_CREATED", e.post()));
    }

    @TransactionalEventListener
    public void on(PostDeletedEvent e) {
        broker.convertAndSend(Topics.feed(e.entranceId()),
                new RealtimeEvent("POST_DELETED", Map.of("postId", e.postId())));
    }

    @TransactionalEventListener
    public void on(PostStatsChangedEvent e) {
        broker.convertAndSend(Topics.feed(e.entranceId()), new RealtimeEvent("POST_STATS",
                Map.of("postId", e.postId(), "likeCount", e.likeCount(), "commentCount", e.commentCount())));
    }

    @TransactionalEventListener
    public void on(CommentCreatedEvent e) {
        broker.convertAndSend(Topics.feed(e.entranceId()), new RealtimeEvent("COMMENT_CREATED", e.comment()));
    }

    // ---- chat ----

    @TransactionalEventListener
    public void on(ChatMessageCreatedEvent e) {
        broker.convertAndSend(Topics.chat(e.entranceId()), new RealtimeEvent("MESSAGE_CREATED", e.message()));
    }

    // ---- polls ----

    @TransactionalEventListener
    public void on(PollCreatedEvent e) {
        broker.convertAndSend(Topics.polls(e.entranceId()), new RealtimeEvent("POLL_CREATED", e.poll()));
    }

    @TransactionalEventListener
    public void on(PollResultsChangedEvent e) {
        broker.convertAndSend(Topics.polls(e.entranceId()), new RealtimeEvent("POLL_RESULTS", e.results()));
    }

    @TransactionalEventListener
    public void on(PollDeletedEvent e) {
        broker.convertAndSend(Topics.polls(e.entranceId()),
                new RealtimeEvent("POLL_DELETED", Map.of("pollId", e.pollId())));
    }

    // ---- noise announcements ----

    @TransactionalEventListener
    public void on(AnnouncementCreatedEvent e) {
        broker.convertAndSend(Topics.announcements(e.entranceId()),
                new RealtimeEvent("ANNOUNCEMENT_CREATED", e.announcement()));
    }

    @TransactionalEventListener
    public void on(AnnouncementUpdatedEvent e) {
        broker.convertAndSend(Topics.announcements(e.entranceId()),
                new RealtimeEvent("ANNOUNCEMENT_UPDATED", e.announcement()));
    }

    @TransactionalEventListener
    public void on(AnnouncementDeletedEvent e) {
        broker.convertAndSend(Topics.announcements(e.entranceId()),
                new RealtimeEvent("ANNOUNCEMENT_DELETED", Map.of("announcementId", e.announcementId())));
    }

    // ---- lost & found ----

    @TransactionalEventListener
    public void on(LostFoundCreatedEvent e) {
        broker.convertAndSend(Topics.lostFound(e.entranceId()), new RealtimeEvent("LOST_FOUND_CREATED", e.item()));
    }

    @TransactionalEventListener
    public void on(LostFoundUpdatedEvent e) {
        broker.convertAndSend(Topics.lostFound(e.entranceId()), new RealtimeEvent("LOST_FOUND_UPDATED", e.item()));
    }

    @TransactionalEventListener
    public void on(LostFoundDeletedEvent e) {
        broker.convertAndSend(Topics.lostFound(e.entranceId()),
                new RealtimeEvent("LOST_FOUND_DELETED", Map.of("itemId", e.itemId())));
    }

    // ---- costs (topic kept as "fees" for existing clients) ----

    @TransactionalEventListener
    public void on(CostsChangedEvent e) {
        broker.convertAndSend(Topics.entrancePrefix(e.entranceId()) + "fees",
                new RealtimeEvent("FEES_UPDATED", Map.of()));
    }

    // ---- presence (not transactional: comes from WebSocket session events) ----

    @EventListener
    public void on(PresenceChangedEvent e) {
        broker.convertAndSend(Topics.presence(e.entranceId()), new RealtimeEvent("PRESENCE", Map.of(
                "userId", e.userId(),
                "online", e.online(),
                "onlineUserIds", presence.onlineUserIds(e.entranceId()))));
    }
}
