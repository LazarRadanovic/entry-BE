-- Free trial + Stripe subscription per entrance. Existing entrances start a fresh 30-day trial.
ALTER TABLE entrance
    ADD COLUMN trial_ends_at          TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '30 days'),
    ADD COLUMN stripe_customer_id     VARCHAR(100) UNIQUE,
    ADD COLUMN stripe_subscription_id VARCHAR(100) UNIQUE,
    ADD COLUMN subscription_status    VARCHAR(30),
    ADD COLUMN current_period_end     TIMESTAMPTZ,
    ADD COLUMN cancel_at_period_end   BOOLEAN NOT NULL DEFAULT FALSE;
