package com.entry.billing;

import com.entry.billing.dto.BillingDto;
import com.entry.billing.dto.RedirectDto;
import com.entry.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Reachable even when the entrance is locked (see SubscriptionEnforcementInterceptor). */
@RestController
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billing;

    /** Any member: tells the UI whether the entrance is in trial, paid or locked. */
    @GetMapping("/api/billing")
    public BillingDto status(@AuthenticationPrincipal AuthUser me) {
        return billing.status(me);
    }

    @PostMapping("/api/admin/billing/checkout")
    public RedirectDto checkout(@AuthenticationPrincipal AuthUser me) {
        return billing.startCheckout(me);
    }

    @PostMapping("/api/admin/billing/portal")
    public RedirectDto portal(@AuthenticationPrincipal AuthUser me) {
        return billing.openPortal(me);
    }

    @PostMapping("/api/admin/billing/sync")
    public BillingDto sync(@AuthenticationPrincipal AuthUser me) {
        return billing.sync(me);
    }

    /** Called by Stripe (public; authenticated by the Stripe-Signature header). */
    @PostMapping("/api/billing/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        billing.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
