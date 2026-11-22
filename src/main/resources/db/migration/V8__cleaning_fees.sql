-- Monthly building-cleaning fee per apartment, tracked by the entrance admin.
ALTER TABLE entrance
    ADD COLUMN fee_amount   NUMERIC(10, 2),
    ADD COLUMN fee_currency VARCHAR(3) NOT NULL DEFAULT 'RSD';

CREATE TABLE fee_unit (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id UUID        NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    floor       VARCHAR(10) NOT NULL,
    apartment   VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fee_unit ON fee_unit (entrance_id, lower(floor), lower(apartment));

CREATE TABLE fee_payment (
    unit_id   UUID          NOT NULL REFERENCES fee_unit (id) ON DELETE CASCADE,
    year      INT           NOT NULL,
    month     INT           NOT NULL CHECK (month BETWEEN 1 AND 12),
    amount    NUMERIC(10, 2),
    marked_by UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    paid_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (unit_id, year, month)
);
