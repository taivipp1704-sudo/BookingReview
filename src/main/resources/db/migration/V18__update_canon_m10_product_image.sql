UPDATE products
SET image_url = '/catalog/products/canon-m10-white-2026.jpg',
    media_revision = COALESCE(media_revision, 0) + 1
WHERE id = 'CAM-CANON-M10'
   OR LOWER(TRIM(name)) IN ('canon m10', 'canon eos m10');
