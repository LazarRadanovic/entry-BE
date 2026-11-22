package com.entry.billing.dto;

import com.entry.billing.BillingState;
import java.time.Instant;

/**
 * Billing status of the caller's entrance.
 * {@code canSubscribe}/{@code canManage} tell an admin which button to show.
 */
public record BillingDto(
        boolean enabled,
        BillingState state,
        boolean accessAllowed,
        Instant trialEndsAt,
        Long trialDaysLeft,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        String priceLabel,
        boolean canSubscribe,
        boolean canManage) {
}
