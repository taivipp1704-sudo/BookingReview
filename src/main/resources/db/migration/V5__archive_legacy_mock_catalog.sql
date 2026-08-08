-- Keep historical booking references intact while removing legacy demo entries
-- from every active customer-facing catalog.
UPDATE products
SET active = FALSE
WHERE id IN (
  'GEAR-001', 'GEAR-002', 'GEAR-003', 'GEAR-004',
  'ACC-001', 'ACC-002', 'ACC-003', 'ACC-004', 'ACC-005', 'ACC-006', 'ACC-007'
);

UPDATE bundles
SET active = FALSE
WHERE id IN ('BND-001', 'BND-002');

-- Canon IXY 650F and Canon IXY 650 intentionally share one approved image.
UPDATE products
SET image_url = '/catalog/products/canon-ixy-600f.webp'
WHERE id IN ('CAM-CANON-IXY-650', 'CAM-CANON-IXY-650F');
