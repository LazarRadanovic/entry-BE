package com.entry.entrance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "entrance")
@Getter
@Setter
@NoArgsConstructor
public class Entrance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(name = "invite_code", nullable = false, unique = true, length = 16)
    private String inviteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ---- cleaning fee (see com.entry.fee) ----

    /** Monthly cleaning fee per apartment; null until the admin sets it. */
    @Column(name = "fee_amount", precision = 10, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "fee_currency", nullable = false, length = 3)
    private String feeCurrency = "RSD";

    // ---- billing (see com.entry.billing) ----

    /** End of the free trial every new entrance gets. */
    @Column(name = "trial_ends_at", nullable = false)
    private Instant trialEndsAt;

    @Column(name = "stripe_customer_id", unique = true, length = 100)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true, length = 100)
    private String stripeSubscriptionId;

    /** Raw Stripe subscription status (active, trialing, past_due, canceled, ...), null before subscribing. */
    @Column(name = "subscription_status", length = 30)
    private String subscriptionStatus;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    public Entrance(String name, String address, String inviteCode, Instant trialEndsAt) {
        this.name = name;
        this.address = address;
        this.inviteCode = inviteCode;
        this.trialEndsAt = trialEndsAt;
    }
}
