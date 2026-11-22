-- Entrance costs: several costs per entrance (building cleaning, garage cleaning, repairs...),
-- each assigned to all apartments or a subset, charged per apartment or split by area.
ALTER TABLE fee_unit
    ADD COLUMN area_m2 NUMERIC(7, 2) CHECK (area_m2 > 0);

CREATE TABLE cost (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id    UUID          NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    name           VARCHAR(100)  NOT NULL,
    description    VARCHAR(500),
    type           VARCHAR(20)   NOT NULL CHECK (type IN ('PER_APARTMENT', 'BY_AREA')),
    amount         NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    currency       VARCHAR(3)    NOT NULL,
    recurrence     VARCHAR(20)   NOT NULL CHECK (recurrence IN ('MONTHLY', 'ONE_TIME')),
    applies_to_all BOOLEAN       NOT NULL DEFAULT TRUE,
    due_date       DATE,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_cost_entrance ON cost (entrance_id);

-- Apartments a cost applies to (only used when applies_to_all = false).
CREATE TABLE cost_unit (
    cost_id UUID NOT NULL REFERENCES cost (id) ON DELETE CASCADE,
    unit_id UUID NOT NULL REFERENCES fee_unit (id) ON DELETE CASCADE,
    PRIMARY KEY (cost_id, unit_id)
);

-- period: 'YYYY-MM' for monthly costs, 'ONCE' for one-time costs. amount = the share due when it was marked.
CREATE TABLE cost_payment (
    cost_id   UUID          NOT NULL REFERENCES cost (id) ON DELETE CASCADE,
    unit_id   UUID          NOT NULL REFERENCES fee_unit (id) ON DELETE CASCADE,
    period    VARCHAR(7)    NOT NULL CHECK (period = 'ONCE' OR period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    amount    NUMERIC(10, 2),
    marked_by UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    paid_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (cost_id, unit_id, period)
);

-- The old single cleaning fee becomes a monthly per-apartment cost for everyone.
INSERT INTO cost (entrance_id, name, type, amount, currency, recurrence, applies_to_all)
SELECT e.id, 'Čišćenje zgrade', 'PER_APARTMENT', COALESCE(e.fee_amount, 0), e.fee_currency, 'MONTHLY', TRUE
FROM entrance e
WHERE e.fee_amount IS NOT NULL
   OR EXISTS (SELECT 1 FROM fee_payment p JOIN fee_unit u ON u.id = p.unit_id WHERE u.entrance_id = e.id);

INSERT INTO cost_payment (cost_id, unit_id, period, amount, marked_by, paid_at)
SELECT c.id, p.unit_id, p.year || '-' || lpad(p.month::text, 2, '0'), p.amount, p.marked_by, p.paid_at
FROM fee_payment p
JOIN fee_unit u ON u.id = p.unit_id
JOIN cost c ON c.entrance_id = u.entrance_id;

DROP TABLE fee_payment;
ALTER TABLE entrance
    DROP COLUMN fee_amount,
    DROP COLUMN fee_currency;

-- Craftsmen / handymen recommended within an entrance.
CREATE TABLE craftsman (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entrance_id   UUID          NOT NULL REFERENCES entrance (id) ON DELETE CASCADE,
    name          VARCHAR(100)  NOT NULL,
    trade         VARCHAR(20)   NOT NULL,
    phone         VARCHAR(30)   NOT NULL,
    email         VARCHAR(254),
    website       VARCHAR(255),
    description   VARCHAR(1000),
    photo_file_id UUID          REFERENCES stored_file (id) ON DELETE SET NULL,
    created_by    UUID          REFERENCES app_user (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_craftsman_entrance ON craftsman (entrance_id);

CREATE TABLE craftsman_recommendation (
    craftsman_id UUID        NOT NULL REFERENCES craftsman (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (craftsman_id, user_id)
);
