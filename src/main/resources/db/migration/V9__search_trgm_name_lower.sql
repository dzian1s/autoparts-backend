CREATE INDEX IF NOT EXISTS idx_products_name_lower_trgm
    ON products USING GIN (lower(name) gin_trgm_ops);