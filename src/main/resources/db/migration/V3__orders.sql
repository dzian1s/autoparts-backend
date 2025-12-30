CREATE TABLE orders (
                        id UUID PRIMARY KEY,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        status TEXT NOT NULL DEFAULT 'NEW',
                        customer_name TEXT,
                        customer_phone TEXT,
                        customer_comment TEXT
);

CREATE TABLE order_items (
                             id UUID PRIMARY KEY,
                             order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                             product_id UUID NOT NULL REFERENCES products(id),
                             qty INT NOT NULL CHECK (qty > 0),
                             price_cents INT NOT NULL CHECK (price_cents >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);