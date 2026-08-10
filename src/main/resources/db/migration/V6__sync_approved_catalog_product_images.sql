-- Keep product photos consistent across the customer catalog and admin inventory.
-- Canon IXY 650 and Canon IXY 650F intentionally use the approved IXY 600F image.
UPDATE products
SET image_url = CASE LOWER(TRIM(name))
  WHEN 'canon r50' THEN '/catalog/products/canon-r50.jpg'
  WHEN 'canon m50' THEN '/catalog/products/canon-m50.jpg'
  WHEN 'canon g7x m2' THEN '/catalog/products/canon-g7x-m2.webp'
  WHEN 'canon m100' THEN '/catalog/products/canon-m100.jpg'
  WHEN 'canon m200' THEN '/catalog/products/canon-m200.jpg'
  WHEN 'canon m10' THEN '/catalog/products/canon-m10.jpg'
  WHEN 'fuji xa5' THEN '/catalog/products/fuji-xa5.jpg'
  WHEN 'fuji xm5' THEN '/catalog/products/fuji-xm5.jpg'
  WHEN 'canon ixy 600f' THEN '/catalog/products/canon-ixy-600f.webp'
  WHEN 'canon ixy 650' THEN '/catalog/products/canon-ixy-600f.webp'
  WHEN 'canon ixy 650f' THEN '/catalog/products/canon-ixy-600f.webp'
  WHEN 'canon m6' THEN '/catalog/products/canon-m6.jpg'
  WHEN 'pocket 3' THEN '/catalog/products/pocket-3.jpg'
  ELSE image_url
END
WHERE LOWER(TRIM(name)) IN (
  'canon r50', 'canon m50', 'canon g7x m2', 'canon m100', 'canon m200',
  'canon m10', 'fuji xa5', 'fuji xm5', 'canon ixy 600f', 'canon ixy 650',
  'canon ixy 650f', 'canon m6', 'pocket 3'
);
