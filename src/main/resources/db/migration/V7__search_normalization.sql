-- products: нормализованные поля
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS part_number_norm text
    GENERATED ALWAYS AS (upper(regexp_replace(part_number, '[^A-Za-z0-9]+', '', 'g'))) STORED,
    ADD COLUMN IF NOT EXISTS oem_number_norm text
    GENERATED ALWAYS AS (upper(regexp_replace(oem_number, '[^A-Za-z0-9]+', '', 'g'))) STORED;

-- cross refs: нормализованное значение
ALTER TABLE product_cross_refs
    ADD COLUMN IF NOT EXISTS ref_value_norm text
    GENERATED ALWAYS AS (upper(regexp_replace(ref_value, '[^A-Za-z0-9]+', '', 'g'))) STORED;

-- Индексы для EXACT
CREATE INDEX IF NOT EXISTS idx_products_part_norm ON products(part_number_norm);
CREATE INDEX IF NOT EXISTS idx_products_oem_norm  ON products(oem_number_norm);
CREATE INDEX IF NOT EXISTS idx_cross_ref_norm     ON product_cross_refs(ref_value_norm);
