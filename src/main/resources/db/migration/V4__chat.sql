CREATE TABLE chat_message (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id   UUID          NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    sender_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    text          VARCHAR(2000),
    image_file_id UUID UNIQUE   REFERENCES stored_file (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_message_entrance ON chat_message (entrance_id, created_at DESC, id DESC);
