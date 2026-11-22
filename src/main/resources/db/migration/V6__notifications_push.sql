CREATE TABLE notification (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type            VARCHAR(30)  NOT NULL,
    actor_id        UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    target_id       UUID,
    preview         VARCHAR(200),
    aggregate_count INT          NOT NULL DEFAULT 1,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE read_at IS NULL;

CREATE TABLE notification_settings (
    user_id      UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    new_messages BOOLEAN NOT NULL DEFAULT TRUE,
    new_polls    BOOLEAN NOT NULL DEFAULT TRUE,
    new_posts    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE push_subscription (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    endpoint   VARCHAR(1000) NOT NULL UNIQUE,
    p256dh     VARCHAR(200)  NOT NULL,
    auth       VARCHAR(100)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_subscription_user ON push_subscription (user_id);
