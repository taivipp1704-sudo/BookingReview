-- The workbook was exported through a legacy encoding in one environment.
-- Product names, groups and specifications were normalized in V9; replace the
-- non-essential free-text metadata with a clean, structured import marker.
UPDATE products
SET custom_attributes = JSON_OBJECT(
  'source', 'AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx',
  'group', category,
  'stockImported', TRUE
)
WHERE id LIKE 'ACC-%';
