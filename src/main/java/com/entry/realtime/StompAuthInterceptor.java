package com.entry.realtime;

import com.entry.security.AuthUser;
import com.entry.security.AuthUserAuthentication;
import com.entry.security.AuthUserJwtConverter;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame with the same access token as REST
 * ({@code Authorization: Bearer ...} native header) and only lets members subscribe to
 * their own entrance's topics and their personal queue.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final AuthUserJwtConverter principalLoader;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> throw new MessageDeliveryException("Sending over STOMP is not supported; use the REST API");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK... need no checks
            }
        }
        return message;
    }

    private AuthUserAuthentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing access token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            AuthUser user = principalLoader.loadPrincipal(jwt.getSubject());
            if (!user.isActive()) {
                throw new MessageDeliveryException("Membership is not active");
            }
            return new AuthUserAuthentication(user, jwt.getTokenValue());
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid or expired access token");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        AuthUser user = currentUser(accessor.getUser());
        String destination = accessor.getDestination();
        boolean allowed = destination != null
                && (destination.startsWith(Topics.entrancePrefix(user.entranceId()))
                || destination.equals("/user" + Topics.USER_NOTIFICATIONS));
        if (!allowed) {
            throw new MessageDeliveryException("Not allowed to subscribe to " + destination);
        }
    }

    /** Re-reads the user from the DB so a member removed after connecting cannot subscribe any more. */
    private AuthUser currentUser(Principal principal) {
        if (!(principal instanceof AuthUserAuthentication auth)) {
            throw new MessageDeliveryException("Not authenticated");
        }
        AuthUser fresh = principalLoader.loadPrincipal(auth.getPrincipal().id().toString());
        if (!fresh.isActive()) {
            throw new MessageDeliveryException("Membership is not active");
        }
        return fresh;
    }
}
