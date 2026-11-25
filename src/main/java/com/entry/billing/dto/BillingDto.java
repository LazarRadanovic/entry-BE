package com.entry.billing.dto;

import com.entry.billing.BillingState;
import java.time.Instant;

/**
 * Billing status of the caller's entrance.
 * {@code readOnly}: trial over without a subscription; everything can be read, nothing posted or changed.
 * {@code canSubscribe}/{@code canManage} tell an admin which button to show.
 */
public record BillingDto(
        boolean enabled,
        BillingState state,
        boolean readOnly,
        Instant trialEndsAt,
        Long trialDaysLeft,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        String priceLabel,
        boolean canSubscribe,
        boolean canManage) {
}
