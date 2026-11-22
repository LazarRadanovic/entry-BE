CREATE TABLE poll (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id UUID         NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    author_id   UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    question    VARCHAR(300) NOT NULL,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_poll_entrance ON poll (entrance_id, created_at DESC);

CREATE TABLE poll_vote (
    poll_id    UUID        NOT NULL REFERENCES poll (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    value      BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (poll_id, user_id)
);
