-- Reservation checkout only collects the mandatory 50,000 VND hold payment.
-- Rental and equipment security deposit remain payable before handover.
UPDATE products
SET booking_deposit = 50000
WHERE level_code = 'L1' AND active = TRUE;

-- Canon IXY 650/650F share the approved IXY 650 product photo.
UPDATE products
SET image_url = '/catalog/products/canon-ixy-650.jpg',
    media_revision = COALESCE(media_revision, 0) + 1
WHERE LOWER(TRIM(name)) IN ('canon ixy 650', 'canon ixy 650f');

-- Use the approved Canon G7X M2 product photo supplied by the business.
UPDATE products
SET image_url = '/catalog/products/canon-g7x-m2.png',
    media_revision = COALESCE(media_revision, 0) + 1
WHERE LOWER(TRIM(name)) = 'canon g7x m2';

-- Home delivery/pickup is the default option and remains editable from admin.
INSERT INTO store_branches (
  id, code, name, address, phone, note, active, sort_order, created_at, updated_at
) VALUES (
  'BRANCH-HOME', 'STORE-HOME', 'Nhận máy tận nhà',
  'Địa chỉ khách hàng cung cấp', '',
  'Hình thức nhận mặc định. Shop xác nhận phạm vi và chi phí trước khi duyệt đơn.',
  TRUE, 0, NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  address = VALUES(address),
  note = VALUES(note),
  active = TRUE,
  sort_order = 0,
  updated_at = NOW();
