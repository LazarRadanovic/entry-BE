package com.entry.push;

import com.entry.common.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends Web Push messages to every device of a user. Payload format is the one Angular's
 * service worker ({@code @angular/service-worker}) understands, so clicks open {@code url}.
 */
@Slf4j
@Component
public class WebPushSender {

    private static final int MAX_BODY = 180;

    private final PushSubscriptionRepository subscriptions;
    private final JsonMapper json;
    private final VapidSigner vapid;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WebPushSender(PushSubscriptionRepository subscriptions, JsonMapper json, AppProperties props) {
        this.subscriptions = subscriptions;
        this.json = json;
        AppProperties.Push push = props.push();
        if (push.enabled()) {
            this.vapid = new VapidSigner(push.vapidPublicKey(), push.vapidPrivateKey(), push.subject());
        } else {
            this.vapid = null;
            log.warn("Web Push disabled: set VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY (npx web-push generate-vapid-keys)");
        }
    }

    public boolean isEnabled() {
        return vapid != null;
    }

    public void send(UUID userId, PushMessage message) {
        if (vapid == null) {
            return;
        }
        byte[] payload = payload(message);
        for (PushSubscription sub : subscriptions.findByUserId(userId)) {
            sendOne(sub, payload);
        }
    }

    private void sendOne(PushSubscription sub, byte[] payload) {
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            byte[] body = WebPushCrypto.encrypt(payload, b64.decode(sub.getP256dh()), b64.decode(sub.getAuth()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(sub.getEndpoint()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", vapid.authorizationHeader(sub.getEndpoint()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                subscriptions.deleteByEndpoint(sub.getEndpoint()); // browser unsubscribed or expired
            } else if (status >= 300) {
                log.warn("Push to {} failed with HTTP {}", URI.create(sub.getEndpoint()).getHost(), status);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Push delivery failed: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private byte[] payload(PushMessage m) {
        String body = m.body() == null ? "" : m.body();
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY - 1) + "…";
        }
        Map<String, Object> notification = Map.of(
                "title", m.title(),
                "body", body,
                "icon", "/icons/icon-192x192.png",
                "badge", "/icons/icon-72x72.png",
                "tag", m.tag(),
                "data", Map.of("onActionClick", Map.of("default",
                        Map.of("operation", "navigateLastFocusedOrOpen", "url", m.url()))));
        return json.writeValueAsString(Map.of("notification", notification)).getBytes(StandardCharsets.UTF_8);
    }
}
