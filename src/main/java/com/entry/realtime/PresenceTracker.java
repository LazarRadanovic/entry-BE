package com.entry.realtime;

import com.entry.security.AuthUserAuthentication;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * In-memory "who is online": a user is online while they have at least one STOMP session
 * (several tabs/devices count once). Single-instance only; see {@link WebSocketConfig}.
 */
@Component
@RequiredArgsConstructor
public class PresenceTracker {

    private record Entry(UUID entranceId, Set<String> sessions) {
    }

    private final Map<UUID, Entry> byUser = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        AuthUserAuthentication auth = asAuth(event.getUser());
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (auth == null || sessionId == null) {
            return;
        }
        UUID userId = auth.getPrincipal().id();
        UUID entranceId = auth.getPrincipal().entranceId();
        boolean[] cameOnline = {false};
        byUser.compute(userId, (id, entry) -> {
            if (entry == null) {
                entry = new Entry(entranceId, ConcurrentHashMap.newKeySet());
                cameOnline[0] = true;
            }
            entry.sessions().add(sessionId);
            return entry;
        });
        if (cameOnline[0]) {
            events.publishEvent(new PresenceChangedEvent(entranceId, userId, true));
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        AuthUserAuthentication auth = asAuth(event.getUser());
        if (auth == null) {
            return;
        }
        UUID userId = auth.getPrincipal().id();
        boolean[] wentOffline = {false};
        UUID[] entranceId = {null};
        byUser.computeIfPresent(userId, (id, entry) -> {
            entry.sessions().remove(event.getSessionId());
            if (entry.sessions().isEmpty()) {
                wentOffline[0] = true;
                entranceId[0] = entry.entranceId();
                return null;
            }
            return entry;
        });
        if (wentOffline[0]) {
            events.publishEvent(new PresenceChangedEvent(entranceId[0], userId, false));
        }
    }

    public boolean isOnline(UUID userId) {
        return byUser.containsKey(userId);
    }

    public Set<UUID> onlineUserIds(UUID entranceId) {
        return byUser.entrySet().stream()
                .filter(e -> e.getValue().entranceId().equals(entranceId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static AuthUserAuthentication asAuth(Principal principal) {
        return principal instanceof AuthUserAuthentication a ? a : null;
    }
}
