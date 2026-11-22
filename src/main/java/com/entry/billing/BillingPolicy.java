package com.entry.billing;

import com.entry.common.config.AppProperties;
import com.entry.entrance.Entrance;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Decides whether an entrance may use the app right now. Pure logic, no I/O. */
@Component
public class BillingPolicy {

    private static final Set<String> PAID = Set.of("active", "trialing");

    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public BillingPolicy(AppProperties props) {
        this(props.billing().enabled(), Clock.systemUTC());
    }

    BillingPolicy(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public BillingState state(Entrance e) {
        if (!enabled) {
            return BillingState.FREE;
        }
        Instant now = clock.instant();
        String status = e.getSubscriptionStatus();
        if (status != null && PAID.contains(status)) {
            return BillingState.ACTIVE;
        }
        if ("past_due".equals(status)) {
            return BillingState.PAST_DUE;
        }
        // canceled/unpaid/incomplete: paid time already bought still counts
        if (e.getCurrentPeriodEnd() != null && e.getCurrentPeriodEnd().isAfter(now) && "canceled".equals(status)) {
            return BillingState.ACTIVE;
        }
        if (e.getTrialEndsAt() != null && e.getTrialEndsAt().isAfter(now)) {
            return BillingState.TRIAL;
        }
        return BillingState.EXPIRED;
    }

    public boolean accessAllowed(Entrance e) {
        return state(e) != BillingState.EXPIRED;
    }
}
