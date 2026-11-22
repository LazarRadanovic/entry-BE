CREATE TABLE post (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id   UUID          NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    author_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    text          VARCHAR(2000),
    like_count    INT           NOT NULL DEFAULT 0,
    comment_count INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_post_feed ON post (entrance_id, created_at DESC, id DESC);
CREATE INDEX idx_post_author ON post (author_id, created_at DESC, id DESC);

CREATE TABLE post_image (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id  UUID NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    file_id  UUID NOT NULL UNIQUE REFERENCES stored_file (id) ON DELETE CASCADE,
    position INT  NOT NULL,
    UNIQUE (post_id, position)
);

CREATE TABLE post_like (
    post_id    UUID        NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE post_comment (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id    UUID          NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    text       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_post_comment_post ON post_comment (post_id, created_at);
