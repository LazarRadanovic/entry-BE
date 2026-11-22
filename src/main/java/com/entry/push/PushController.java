package com.entry.push;

import com.entry.common.config.AppProperties;
import com.entry.push.dto.SubscribeRequest;
import com.entry.push.dto.UnsubscribeRequest;
import com.entry.push.dto.VapidKeyDto;
import com.entry.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Open to PENDING users too, so they can be notified when they get approved. */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushSubscriptionService service;
    private final WebPushSender sender;
    private final AppProperties props;

    @GetMapping("/vapid-public-key")
    public VapidKeyDto vapidPublicKey() {
        return new VapidKeyDto(sender.isEnabled(), sender.isEnabled() ? props.push().vapidPublicKey() : null);
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody SubscribeRequest request) {
        service.subscribe(me, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody UnsubscribeRequest request) {
        service.unsubscribe(me, request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
