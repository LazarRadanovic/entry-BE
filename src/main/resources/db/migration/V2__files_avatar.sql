CREATE TABLE stored_file (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id  UUID         NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    uploader_id  UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    width        INT          NOT NULL,
    height       INT          NOT NULL,
    storage_key  VARCHAR(255) NOT NULL,
    thumb_key    VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_stored_file_uploader ON stored_file (uploader_id);

ALTER TABLE app_user
    ADD COLUMN avatar_file_id UUID REFERENCES stored_file (id) ON DELETE SET NULL;
