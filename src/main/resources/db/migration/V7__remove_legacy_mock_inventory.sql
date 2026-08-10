-- The approved AMY catalog replaces the legacy GEAR/ACC demo inventory.
-- Historical bookings retain their snapshots, while the live warehouse only shows approved products.
DELETE FROM inventory_ledger_entries
WHERE product_id IN (
  'GEAR-001', 'GEAR-002', 'GEAR-003', 'GEAR-004',
  'ACC-001', 'ACC-002', 'ACC-003', 'ACC-004', 'ACC-005', 'ACC-006', 'ACC-007'
);

DELETE FROM inventory_assets
WHERE product_id IN (
  'GEAR-001', 'GEAR-002', 'GEAR-003', 'GEAR-004',
  'ACC-001', 'ACC-002', 'ACC-003', 'ACC-004', 'ACC-005', 'ACC-006', 'ACC-007'
);

DELETE FROM stock_items
WHERE product_id IN (
  'GEAR-001', 'GEAR-002', 'GEAR-003', 'GEAR-004',
  'ACC-001', 'ACC-002', 'ACC-003', 'ACC-004', 'ACC-005', 'ACC-006', 'ACC-007'
);
