CREATE INDEX IF NOT EXISTS idx_products_part_norm_trgm
    ON products USING GIN (part_number_norm gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_products_oem_norm_trgm
    ON products USING GIN (oem_number_norm gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_cross_ref_norm_trgm
    ON product_cross_refs USING GIN (ref_value_norm gin_trgm_ops);