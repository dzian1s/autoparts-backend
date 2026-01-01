UPDATE orders SET customer_name = '' WHERE customer_name IS NULL;
UPDATE orders SET customer_phone = '' WHERE customer_phone IS NULL;

ALTER TABLE orders
    ALTER COLUMN customer_name SET NOT NULL,
ALTER COLUMN customer_phone SET NOT NULL;
