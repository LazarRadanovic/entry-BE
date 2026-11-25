package com.entry.billing;

import com.entry.billing.StripeGateway.CheckoutRequest;
import com.entry.billing.StripeGateway.SubscriptionSnapshot;
import com.entry.billing.StripeGateway.WebhookEvent;
import com.entry.billing.dto.BillingDto;
import com.entry.billing.dto.RedirectDto;
import com.entry.common.config.AppProperties;
import com.entry.common.exception.ApiException;
import com.entry.entrance.Entrance;
import com.entry.entrance.EntranceRepository;
import com.entry.security.AuthUser;
import com.entry.user.User;
import com.entry.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Free trial, then a Stripe subscription per entrance. Stripe is the source of truth: we only mirror
 * its subscription status, from webhooks and from an explicit sync after checkout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final Set<String> HANDLED_EVENTS = Set.of(
            "checkout.session.completed",
            "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.deleted",
            "customer.subscription.paused",
            "customer.subscription.resumed",
            "invoice.paid",
            "invoice.payment_failed");

    /** Stripe requires a trial_end at least 48h in the future. */
    private static final Duration MIN_STRIPE_TRIAL = Duration.ofHours(49);

    private final EntranceRepository entrances;
    private final UserRepository users;
    private final BillingPolicy policy;
    private final StripeGateway stripe;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public BillingDto status(AuthUser me) {
        return toDto(load(me), me);
    }

    /** Starts Stripe Checkout. Any trial time left is carried over to the subscription. */
    @Transactional
    public RedirectDto startCheckout(AuthUser me) {
        requireEnabled();
        Entrance e = load(me);
        BillingState state = policy.state(e);
        if (state == BillingState.ACTIVE || state == BillingState.PAST_DUE) {
            throw ApiException.conflict("ALREADY_SUBSCRIBED", "This entrance already has a subscription");
        }
        if (e.getStripeCustomerId() == null) {
            User admin = users.findById(me.id()).orElseThrow();
            e.setStripeCustomerId(stripe.createCustomer(admin.getEmail(), e.getName() + " · " + e.getAddress(),
                    e.getId()));
        }
        Instant trialEnd = e.getTrialEndsAt().isAfter(Instant.now().plus(MIN_STRIPE_TRIAL)) ? e.getTrialEndsAt() : null;
        String base = props.billing().appUrl();
        String url = stripe.createCheckoutSession(new CheckoutRequest(e.getStripeCustomerId(),
                props.billing().stripePriceId(), e.getId(), trialEnd,
                base + "/billing?checkout=success", base + "/billing?checkout=canceled"));
        return new RedirectDto(url);
    }

    /** Stripe Billing Portal: change card, see invoices, cancel. */
    @Transactional(readOnly = true)
    public RedirectDto openPortal(AuthUser me) {
        requireEnabled();
        Entrance e = load(me);
        if (e.getStripeCustomerId() == null) {
            throw ApiException.conflict("NO_SUBSCRIPTION", "Subscribe first");
        }
        return new RedirectDto(stripe.createPortalSession(e.getStripeCustomerId(),
                props.billing().appUrl() + "/billing"));
    }

    /** Pulls the current subscription from Stripe (used right after Checkout, so it works even without webhooks). */
    @Transactional
    public BillingDto sync(AuthUser me) {
        requireEnabled();
        Entrance e = load(me);
        if (e.getStripeCustomerId() != null) {
            stripe.latestSubscription(e.getStripeCustomerId()).ifPresent(s -> apply(e, s));
        }
        return toDto(e, me);
    }

    /** Stripe webhook. Re-reads the subscription from Stripe, so event order and duplicates don't matter. */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        requireEnabled();
        WebhookEvent event;
        try {
            event = stripe.verifyWebhook(payload, signature);
        } catch (StripeGateway.InvalidWebhookException ex) {
            throw ApiException.badRequest("INVALID_WEBHOOK", ex.getMessage());
        }
        if (!HANDLED_EVENTS.contains(event.type()) || event.subscriptionId() == null) {
            return;
        }
        Optional<SubscriptionSnapshot> snapshot = stripe.retrieveSubscription(event.subscriptionId());
        if (snapshot.isEmpty()) {
            return;
        }
        SubscriptionSnapshot s = snapshot.get();
        Optional<Entrance> entrance = findEntrance(s.entranceId())
                .or(() -> findEntrance(event.entranceId()))
                .or(() -> Optional.ofNullable(s.customerId()).flatMap(entrances::findByStripeCustomerId));
        entrance.ifPresentOrElse(e -> apply(e, s),
                () -> log.warn("Stripe event {} for unknown subscription {}", event.type(), s.id()));
    }

    private void apply(Entrance e, SubscriptionSnapshot s) {
        e.setStripeCustomerId(s.customerId());
        e.setStripeSubscriptionId(s.id());
        e.setSubscriptionStatus(s.status());
        e.setCurrentPeriodEnd(s.currentPeriodEnd());
        e.setCancelAtPeriodEnd(s.cancelAtPeriodEnd());
        log.info("Entrance {} subscription {} is now {}", e.getId(), s.id(), s.status());
    }

    private Optional<Entrance> findEntrance(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return entrances.findById(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private BillingDto toDto(Entrance e, AuthUser me) {
        BillingState state = policy.state(e);
        Long daysLeft = state == BillingState.TRIAL
                ? Math.max(0, (long) Math.ceil(Duration.between(Instant.now(), e.getTrialEndsAt()).toHours() / 24.0))
                : null;
        boolean admin = me.isAdmin() && policy.isEnabled();
        return new BillingDto(
                policy.isEnabled(),
                state,
                state == BillingState.EXPIRED,
                e.getTrialEndsAt(),
                daysLeft,
                e.getCurrentPeriodEnd(),
                e.isCancelAtPeriodEnd(),
                props.billing().priceLabel(),
                admin && (state == BillingState.TRIAL || state == BillingState.EXPIRED),
                admin && e.getStripeCustomerId() != null);
    }

    private Entrance load(AuthUser me) {
        return entrances.findById(me.entranceId()).orElseThrow(() -> ApiException.notFound("Entrance not found"));
    }

    private void requireEnabled() {
        if (!policy.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_NOT_CONFIGURED",
                    "Billing is not configured on this server");
        }
    }
}
