-- Makes a product image URL versioned whenever an admin changes its media.
ALTER TABLE products
  ADD COLUMN IF NOT EXISTS media_revision BIGINT NOT NULL DEFAULT 1;
