ALTER TABLE orders
    ADD COLUMN privacy_accepted_at TIMESTAMPTZ NOT NULL DEFAULT now();