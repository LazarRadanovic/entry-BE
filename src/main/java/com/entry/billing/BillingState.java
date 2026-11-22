package com.entry.billing;

/** What the UI shows. Only EXPIRED locks the entrance. */
public enum BillingState {
    /** Free trial running, no subscription yet. */
    TRIAL,
    /** Paid (or Stripe-side trial with a card on file). */
    ACTIVE,
    /** Last payment failed; Stripe is retrying. Still usable. */
    PAST_DUE,
    /** Trial over and no working subscription: the app is locked until an admin subscribes. */
    EXPIRED,
    /** Stripe is not configured on this server: nothing is enforced. */
    FREE
}
