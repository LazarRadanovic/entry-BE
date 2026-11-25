-- Lost & found in the entrance: keys, packages, toys, pets found (or lost) in the hallway or in front of the building.
CREATE TABLE lost_found_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id   UUID          NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    author_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind          VARCHAR(10)   NOT NULL CHECK (kind IN ('FOUND', 'LOST')),
    category      VARCHAR(20)   NOT NULL
        CHECK (category IN ('KEYS', 'PACKAGE', 'PET', 'TOY', 'DOCUMENTS', 'CLOTHING', 'OTHER')),
    title         VARCHAR(100)  NOT NULL,
    description   VARCHAR(1000),
    location      VARCHAR(100),
    photo_file_id UUID          REFERENCES stored_file (id) ON DELETE SET NULL,
    resolved_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_lost_found_entrance ON lost_found_item (entrance_id, created_at DESC);

-- Neighbours who answered "it's mine" (FOUND) or "I found it" (LOST).
CREATE TABLE lost_found_response (
    item_id    UUID        NOT NULL REFERENCES lost_found_item (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (item_id, user_id)
);
