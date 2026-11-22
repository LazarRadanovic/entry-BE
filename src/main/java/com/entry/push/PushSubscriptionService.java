package com.entry.push;

import com.entry.common.exception.ApiException;
import com.entry.push.dto.SubscribeRequest;
import com.entry.security.AuthUser;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    /**
     * The server POSTs to whatever endpoint a client registers, so only real push services are accepted
     * (prevents using the backend to call internal URLs, i.e. SSRF).
     */
    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of(
            "fcm.googleapis.com", "android.googleapis.com", // Chrome, Edge (Chromium), Android
            "push.services.mozilla.com",                    // Firefox
            "push.apple.com",                               // Safari / iOS
            "notify.windows.com");                          // legacy Edge

    private final PushSubscriptionRepository subscriptions;

    @Transactional
    public void subscribe(AuthUser me, SubscribeRequest req) {
        validateEndpoint(req.endpoint());
        validateKeys(req.keys());
        PushSubscription sub = subscriptions.findByEndpoint(req.endpoint()).orElseGet(PushSubscription::new);
        sub.setUserId(me.id()); // a shared device moves to whoever subscribed last
        sub.setEndpoint(req.endpoint());
        sub.setP256dh(req.keys().p256dh());
        sub.setAuth(req.keys().auth());
        subscriptions.save(sub);
    }

    @Transactional
    public void unsubscribe(AuthUser me, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint, me.id());
    }

    private static void validateEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = "https".equals(uri.getScheme())
                && ALLOWED_HOST_SUFFIXES.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
        if (!allowed) {
            throw invalid();
        }
    }

    private static void validateKeys(SubscribeRequest.Keys keys) {
        try {
            WebPushCrypto.decodePublic(Base64.getUrlDecoder().decode(keys.p256dh()));
            if (Base64.getUrlDecoder().decode(keys.auth()).length != 16) {
                throw invalid();
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("INVALID_PUSH_SUBSCRIPTION", "Unsupported or malformed push subscription");
    }
}
