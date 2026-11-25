-- "I'm renovating / having a celebration": a resident tells the neighbours about noise in advance.
-- from_time/to_time are the daily hours (both null = all day); to_time < from_time means it runs past midnight.
CREATE TABLE announcement (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id UUID         NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    author_id   UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind        VARCHAR(20)  NOT NULL CHECK (kind IN ('RENOVATION', 'CELEBRATION', 'MOVING', 'OTHER')),
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL CHECK (end_date >= start_date),
    from_time   TIME,
    to_time     TIME,
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((from_time IS NULL) = (to_time IS NULL))
);
CREATE INDEX idx_announcement_entrance ON announcement (entrance_id, end_date);
