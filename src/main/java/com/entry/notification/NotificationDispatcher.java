package com.entry.notification;

import com.entry.announcement.dto.AnnouncementDto;
import com.entry.announcement.event.AnnouncementCreatedEvent;
import com.entry.billing.TrialEndingEvent;
import com.entry.lostfound.LostFoundKind;
import com.entry.lostfound.dto.LostFoundDto;
import com.entry.lostfound.event.LostFoundCreatedEvent;
import com.entry.lostfound.event.LostFoundRespondedEvent;
import com.entry.chat.event.ChatMessageCreatedEvent;
import com.entry.entrance.EntranceRepository;
import com.entry.feed.event.CommentCreatedEvent;
import com.entry.feed.event.PostCreatedEvent;
import com.entry.feed.event.PostLikedEvent;
import com.entry.poll.event.PollCreatedEvent;
import com.entry.push.PushMessage;
import com.entry.push.WebPushSender;
import com.entry.realtime.PresenceTracker;
import com.entry.realtime.RealtimeEvent;
import com.entry.realtime.Topics;
import com.entry.user.Role;
import com.entry.user.User;
import com.entry.user.UserRepository;
import com.entry.user.UserStatus;
import com.entry.user.dto.ResidentDto;
import com.entry.user.event.JoinRequestedEvent;
import com.entry.user.event.MemberApprovedEvent;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into notifications, after the triggering transaction commits, on a background
 * (virtual) thread. Delivery: online users get it over STOMP, offline users get a Web Push
 * (respecting their settings). Likes are live-only (never pushed). Chat messages are push-only for
 * offline members and are not stored, so the notification list doesn't fill up with chat.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final int PREVIEW = 120;

    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final UserRepository users;
    private final EntranceRepository entrances;
    private final PresenceTracker presence;
    private final SimpMessagingTemplate broker;
    private final WebPushSender push;

    private enum Category { MESSAGES, POLLS, POSTS, ALWAYS }

    // ---------- feed ----------

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PostCreatedEvent e) {
        ResidentDto author = e.post().author();
        String preview = e.post().text() != null ? snippet(e.post().text())
                : "shared " + (e.post().images().size() == 1 ? "a photo" : e.post().images().size() + " photos");
        PushMessage msg = new PushMessage("New post", fullName(author) + ": " + preview, "/feed",
                "post-" + e.post().id());
        for (User u : activeMembersExcept(e.entranceId(), author.id())) {
            deliver(u, store(u.getId(), NotificationType.NEW_POST, author.id(), e.post().id(), preview),
                    Category.POSTS, msg);
        }
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(CommentCreatedEvent e) {
        ResidentDto commenter = e.comment().author();
        if (commenter.id().equals(e.postAuthorId())) {
            return;
        }
        users.findById(e.postAuthorId()).filter(User::isActive).ifPresent(postAuthor -> {
            String preview = snippet(e.comment().text());
            Notification n = store(postAuthor.getId(), NotificationType.NEW_COMMENT, commenter.id(),
                    e.comment().postId(), preview);
            deliver(postAuthor, n, Category.POSTS, new PushMessage(fullName(commenter) + " commented on your post",
                    preview, "/feed/" + e.comment().postId(), "comment-" + e.comment().postId()));
        });
    }

    /** Likes on the same post fold into one unread notification ("Marko and 4 others…"); never pushed. */
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PostLikedEvent e) {
        if (e.likerId().equals(e.postAuthorId())) {
            return;
        }
        Notification n = notifications.findFirstByUserIdAndTypeAndTargetIdAndReadAtIsNull(e.postAuthorId(),
                        NotificationType.POST_LIKED, e.postId())
                .map(existing -> {
                    existing.setActor(users.getReferenceById(e.likerId()));
                    existing.setAggregateCount(existing.getAggregateCount() + 1);
                    existing.setCreatedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> store(e.postAuthorId(), NotificationType.POST_LIKED, e.likerId(), e.postId(), null));
        sendLive(e.postAuthorId(), n);
    }

    // ---------- polls ----------

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PollCreatedEvent e) {
        String question = snippet(e.poll().question());
        PushMessage msg = new PushMessage("New poll", question, "/polls", "poll-" + e.poll().id());
        for (User u : activeMembersExcept(e.entranceId(), e.authorId())) {
            deliver(u, store(u.getId(), NotificationType.NEW_POLL, e.authorId(), e.poll().id(), question),
                    Category.POLLS, msg);
        }
    }

    // ---------- noise announcements ----------

    /** Sent to everyone (only the push master switch applies): the point is that nobody is surprised. */
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AnnouncementCreatedEvent e) {
        AnnouncementDto a = e.announcement();
        String when = when(a);
        String body = a.note() == null ? when : when + " · " + snippet(a.note());
        PushMessage msg = new PushMessage(kindLabel(a) + " · apt " + a.author().apartment(), body, "/announcements",
                "announcement-" + a.id());
        for (User u : activeMembersExcept(e.entranceId(), a.author().id())) {
            deliver(u, store(u.getId(), NotificationType.NEW_ANNOUNCEMENT, a.author().id(), a.id(), when),
                    Category.ALWAYS, msg);
        }
    }

    // ---------- lost & found ----------

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LostFoundCreatedEvent e) {
        LostFoundDto item = e.item();
        String title = snippet(item.title());
        String where = item.location() == null ? "" : " · " + snippet(item.location());
        PushMessage msg = new PushMessage(item.kind() == LostFoundKind.FOUND ? "Found in the building" : "Lost",
                title + where, "/lost-found", "lost-found-" + item.id());
        for (User u : activeMembersExcept(e.entranceId(), item.author().id())) {
            deliver(u, store(u.getId(), NotificationType.NEW_LOST_FOUND, item.author().id(), item.id(),
                    item.kind() + ": " + title), Category.POSTS, msg);
        }
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LostFoundRespondedEvent e) {
        users.findById(e.authorId()).filter(User::isActive).ifPresent(author -> {
            User responder = users.findById(e.responderId()).orElse(null);
            if (responder == null) {
                return;
            }
            String name = responder.getFirstName() + " " + responder.getLastName();
            String title = snippet(e.title());
            String body = e.kind() == LostFoundKind.FOUND ? name + " says it's theirs: " + title
                    : name + " found it: " + title;
            deliver(author, store(author.getId(), NotificationType.LOST_FOUND_RESPONSE, e.responderId(), e.itemId(),
                    title), Category.ALWAYS, new PushMessage("Lost & found", body, "/lost-found",
                    "lost-found-response-" + e.itemId()));
        });
    }

    // ---------- chat (push only) ----------

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(ChatMessageCreatedEvent e) {
        ResidentDto sender = e.message().sender();
        String body = e.message().text() != null ? snippet(e.message().text()) : "📷 Photo";
        PushMessage msg = new PushMessage(fullName(sender), body, "/chat", "chat");
        for (User u : activeMembersExcept(e.entranceId(), sender.id())) {
            if (!presence.isOnline(u.getId()) && pushAllowed(u.getId(), Category.MESSAGES)) {
                push.send(u.getId(), msg);
            }
        }
    }

    // ---------- membership ----------

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(JoinRequestedEvent e) {
        User applicant = users.findById(e.userId()).orElse(null);
        if (applicant == null) {
            return;
        }
        String name = applicant.getFirstName() + " " + applicant.getLastName();
        PushMessage msg = new PushMessage("New join request", name + " wants to join your entrance",
                "/admin/members", "join-request");
        List<User> admins = users.findByEntrance_IdAndStatusAndRole(e.entranceId(), UserStatus.ACTIVE,
                Role.ENTRANCE_ADMIN);
        for (User admin : admins) {
            deliver(admin, store(admin.getId(), NotificationType.JOIN_REQUEST, e.userId(), e.userId(), null),
                    Category.ALWAYS, msg);
        }
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(MemberApprovedEvent e) {
        String entranceName = entrances.findById(e.entranceId()).map(en -> en.getName()).orElse("your entrance");
        users.findById(e.userId()).ifPresent(u -> {
            Notification n = store(u.getId(), NotificationType.MEMBERSHIP_APPROVED, e.approvedById(), e.entranceId(),
                    entranceName);
            deliver(u, n, Category.ALWAYS, new PushMessage("You're in!",
                    "Your request to join " + entranceName + " was approved", "/feed", "membership"));
        });
    }

    // ---------- billing ----------

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(TrialEndingEvent e) {
        String days = e.daysLeft() == 1 ? "1 day" : e.daysLeft() + " days";
        PushMessage msg = new PushMessage("Free trial ends in " + days,
                "Subscribe to keep your entrance running", "/billing", "trial-ending");
        for (User admin : users.findByEntrance_IdAndStatusAndRole(e.entranceId(), UserStatus.ACTIVE,
                Role.ENTRANCE_ADMIN)) {
            deliver(admin, store(admin.getId(), NotificationType.TRIAL_ENDING, null, e.entranceId(), days),
                    Category.ALWAYS, msg);
        }
    }

    // ---------- helpers ----------

    private Notification store(UUID recipientId, NotificationType type, UUID actorId, UUID targetId, String preview) {
        Notification n = new Notification();
        n.setUserId(recipientId);
        n.setType(type);
        n.setActor(actorId == null ? null : users.getReferenceById(actorId));
        n.setTargetId(targetId);
        n.setPreview(preview);
        return notifications.save(n);
    }

    private void deliver(User recipient, Notification n, Category category, PushMessage msg) {
        if (presence.isOnline(recipient.getId())) {
            sendLive(recipient.getId(), n);
        } else if (pushAllowed(recipient.getId(), category)) {
            push.send(recipient.getId(), msg);
        }
    }

    private void sendLive(UUID recipientId, Notification n) {
        if (presence.isOnline(recipientId)) {
            notifications.flush();
            broker.convertAndSendToUser(recipientId.toString(), Topics.USER_NOTIFICATIONS,
                    new RealtimeEvent("NOTIFICATION", notificationService.toDto(n)));
        }
    }

    private boolean pushAllowed(UUID userId, Category category) {
        NotificationSettings s = notificationService.settingsFor(userId);
        if (!s.isPushEnabled()) {
            return false;
        }
        Predicate<NotificationSettings> rule = switch (category) {
            case MESSAGES -> NotificationSettings::isNewMessages;
            case POLLS -> NotificationSettings::isNewPolls;
            case POSTS -> NotificationSettings::isNewPosts;
            case ALWAYS -> x -> true;
        };
        return rule.test(s);
    }

    private List<User> activeMembersExcept(UUID entranceId, UUID excludedUserId) {
        return users.findByEntrance_IdAndStatusOrderByCreatedAtAsc(entranceId, UserStatus.ACTIVE).stream()
                .filter(u -> !u.getId().equals(excludedUserId))
                .toList();
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d.M.");

    /** e.g. "1.12.–5.12. · 08:00–16:00" or "6.12. · all day". */
    private static String when(AnnouncementDto a) {
        String days = DAY.format(a.startDate()) + (a.endDate().equals(a.startDate()) ? ""
                : "–" + DAY.format(a.endDate()));
        String hours = a.fromTime() == null ? "all day" : a.fromTime() + "–" + a.toTime();
        return days + " · " + hours;
    }

    private static String kindLabel(AnnouncementDto a) {
        return switch (a.kind()) {
            case RENOVATION -> "Renovation";
            case CELEBRATION -> "Celebration";
            case MOVING -> "Moving";
            case OTHER -> "Noise notice";
        };
    }

    private static String fullName(ResidentDto r) {
        return r.firstName() + " " + r.lastName();
    }

    private static String snippet(String text) {
        String t = text.strip().replaceAll("\\s+", " ");
        return t.length() <= PREVIEW ? t : t.substring(0, PREVIEW - 1) + "…";
    }
}
