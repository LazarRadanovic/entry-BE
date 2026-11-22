CREATE TABLE entrance (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(200) NOT NULL,
    invite_code VARCHAR(16)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    first_name    VARCHAR(50)  NOT NULL,
    last_name     VARCHAR(50)  NOT NULL,
    floor         VARCHAR(10)  NOT NULL,
    apartment     VARCHAR(10)  NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('RESIDENT', 'ENTRANCE_ADMIN')),
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED')),
    entrance_id   UUID         NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_user_entrance_status ON app_user (entrance_id, status);

CREATE TABLE refresh_token (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
