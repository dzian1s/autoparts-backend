CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE OR REPLACE FUNCTION products_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('simple', coalesce(NEW.name,'')), 'A') ||
    setweight(to_tsvector('simple', coalesce(NEW.description,'')), 'B') ||
    setweight(to_tsvector('simple', coalesce(NEW.part_number,'')), 'A') ||
    setweight(to_tsvector('simple', coalesce(NEW.oem_number,'')), 'A');
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_products_search_vector ON products;
CREATE TRIGGER trg_products_search_vector
    BEFORE INSERT OR UPDATE OF name, description, part_number, oem_number
                     ON products
                         FOR EACH ROW EXECUTE FUNCTION products_search_vector_update();

UPDATE products SET search_vector =
                        setweight(to_tsvector('simple', coalesce(name,'')), 'A') ||
                        setweight(to_tsvector('simple', coalesce(description,'')), 'B') ||
                        setweight(to_tsvector('simple', coalesce(part_number,'')), 'A') ||
                        setweight(to_tsvector('simple', coalesce(oem_number,'')), 'A');

CREATE INDEX IF NOT EXISTS idx_products_search_vector ON products USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_part_trgm ON products USING GIN (part_number gin_trgm_ops);
