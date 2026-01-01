ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS client_id uuid;

CREATE INDEX IF NOT EXISTS idx_orders_client_id ON orders(client_id);
