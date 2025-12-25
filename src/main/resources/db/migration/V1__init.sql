DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
CREATE TYPE user_role AS ENUM ('CLIENT', 'ADMIN');
END IF;
END $$;

CREATE TABLE IF NOT EXISTS users (
                                     id              UUID PRIMARY KEY,
                                     email           TEXT NOT NULL UNIQUE,
                                     password_hash   TEXT NOT NULL,
                                     role            user_role NOT NULL,
                                     created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS categories (
                                          id          UUID PRIMARY KEY,
                                          name        TEXT NOT NULL,
                                          parent_id   UUID NULL REFERENCES categories(id)
    );

CREATE TABLE IF NOT EXISTS brands (
                                      id     UUID PRIMARY KEY,
                                      name   TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS products (
                                        id           UUID PRIMARY KEY,
                                        name         TEXT NOT NULL,
                                        description  TEXT NOT NULL DEFAULT '',
                                        brand_id     UUID REFERENCES brands(id),
    category_id  UUID REFERENCES categories(id),
    part_number  TEXT NOT NULL,
    oem_number   TEXT NOT NULL DEFAULT '',
    price_cents  INT NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_products_part_number ON products(part_number);
CREATE INDEX IF NOT EXISTS idx_products_oem_number ON products(oem_number);

CREATE TABLE IF NOT EXISTS product_cross_refs (
                                                  id         UUID PRIMARY KEY,
                                                  product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    ref_type   TEXT NOT NULL,
    ref_value  TEXT NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_cross_refs_value ON product_cross_refs(ref_value);

CREATE TABLE IF NOT EXISTS inventory (
                                         product_id        UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    quantity          INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
