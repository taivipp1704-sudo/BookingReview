-- Make included kit components understandable to customers while preserving IDs,
-- pricing, compensation references and current stock quantities.
UPDATE products SET
  name = CASE id
    WHEN 'ACC-BAT-LPE17' THEN 'Pin Canon LP-E17'
    WHEN 'ACC-BAT-LPE12' THEN 'Pin Canon LP-E12'
    WHEN 'ACC-BAT-NB13L' THEN 'Pin Canon NB-13L'
    WHEN 'ACC-BAT-NPW126S' THEN 'Pin Fujifilm NP-W126S'
    WHEN 'ACC-BAT-NB4L' THEN 'Pin Canon NB-4L'
    WHEN 'ACC-BAT-NB11LH' THEN 'Pin Canon NB-11LH'
    WHEN 'ACC-POWER-POCKET3' THEN 'Pin dự phòng Pocket 3'
    WHEN 'ACC-CHG-LCE17' THEN 'Đồ sạc Canon LP-E17'
    WHEN 'ACC-CHG-LCE12' THEN 'Đồ sạc Canon LP-E12'
    WHEN 'ACC-CHG-NB13L' THEN 'Đồ sạc Canon NB-13L'
    WHEN 'ACC-CHG-NPW126S' THEN 'Đồ sạc Fujifilm NP-W126S'
    WHEN 'ACC-CHG-NB4L' THEN 'Đồ sạc Canon NB-4L'
    WHEN 'ACC-CHG-CB2LFE' THEN 'Đồ sạc Canon NB-11LH'
    WHEN 'ACC-CHG-USBC' THEN 'Đồ sạc USB-C và cáp'
    WHEN 'ACC-CARD-SD64' THEN 'Thẻ nhớ SD 64GB UHS-I V30'
    WHEN 'ACC-CARD-SD128' THEN 'Thẻ nhớ SD 128GB UHS-I V30'
    WHEN 'ACC-CARD-MICROSD128' THEN 'Thẻ nhớ microSD 128GB U3 V30'
    WHEN 'ACC-READER-SD' THEN 'Đầu đọc thẻ nhớ SD USB-C/USB-A'
    WHEN 'ACC-READER-MICROSD' THEN 'Đầu đọc thẻ nhớ microSD USB-C/USB-A'
    WHEN 'ACC-STRAP' THEN 'Dây đeo máy ảnh'
    WHEN 'ACC-SKIN' THEN 'Ốp máy ảnh'
    WHEN 'ACC-BAG-KIT' THEN 'Túi chống sốc'
    ELSE name
  END,
  category = CASE
    WHEN id LIKE 'ACC-BAT-%' OR id = 'ACC-POWER-POCKET3' THEN 'Pin'
    WHEN id LIKE 'ACC-CHG-%' THEN 'Đồ sạc'
    WHEN id LIKE 'ACC-CARD-%' THEN 'Thẻ nhớ'
    WHEN id LIKE 'ACC-READER-%' THEN 'Đầu đọc thẻ nhớ'
    WHEN id = 'ACC-STRAP' THEN 'Dây đeo'
    WHEN id = 'ACC-SKIN' THEN 'Ốp máy ảnh'
    WHEN id = 'ACC-BAG-KIT' THEN 'Túi chống sốc'
    ELSE category
  END
WHERE id IN (
  'ACC-BAT-LPE17', 'ACC-BAT-LPE12', 'ACC-BAT-NB13L', 'ACC-BAT-NPW126S',
  'ACC-BAT-NB4L', 'ACC-BAT-NB11LH', 'ACC-POWER-POCKET3',
  'ACC-CHG-LCE17', 'ACC-CHG-LCE12', 'ACC-CHG-NB13L', 'ACC-CHG-NPW126S',
  'ACC-CHG-NB4L', 'ACC-CHG-CB2LFE', 'ACC-CHG-USBC',
  'ACC-CARD-SD64', 'ACC-CARD-SD128', 'ACC-CARD-MICROSD128',
  'ACC-READER-SD', 'ACC-READER-MICROSD', 'ACC-STRAP', 'ACC-SKIN', 'ACC-BAG-KIT'
);

-- Fuji XM5 includes one detachable Medalight F1 flash in its standard kit.
INSERT INTO products (
  id, level_code, name, brand, category,
  hourly_price, half_day_price, daily_price, two_day_price, multi_day_price,
  multi_day_days, extra_day_price, equipment_deposit, booking_deposit,
  late_fee_per_hour, identity_violation_fee, unauthorized_transfer_fee,
  impact_penalty_percent, damage_liability_limit,
  included, active, image_url, specs, tracking_mode, serial_prefix,
  store_branch_id, booking_count_base, custom_attributes
) VALUES (
  'ACC-FLASH-MEDALIGHT-F1', 'L4', 'Đèn flash rời Medalight F1', 'Medalight', 'Đèn flash',
  0, 0, 0, 0, 0, 3, 0, 0, 0,
  0, 0, 0, 0, 0,
  TRUE, TRUE, '/catalog/products/medalight-f1.png',
  'Đèn flash rời Medalight F1 đi kèm Fuji XM5', 'QUANTITY', '',
  NULL, 0,
  '{"group":"Phụ kiện","includedWith":"Fuji XM5","description":"Đèn flash rời Medalight F1 đi kèm bộ Fuji XM5."}'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name), brand = VALUES(brand), category = VALUES(category),
  included = TRUE, active = TRUE, image_url = VALUES(image_url),
  specs = VALUES(specs), custom_attributes = VALUES(custom_attributes),
  media_revision = COALESCE(media_revision, 0) + 1;

INSERT INTO stock_items (product_id, total_qty, in_use_qty)
VALUES ('ACC-FLASH-MEDALIGHT-F1', 1, 0)
ON DUPLICATE KEY UPDATE total_qty = GREATEST(total_qty, 1);

-- Older imported cameras now expose the same complete detail structure as G7X.
UPDATE products
SET specs = COALESCE(NULLIF(TRIM(specs), ''), CONCAT('Thông số kỹ thuật ', name)),
    custom_attributes = JSON_SET(
      COALESCE(NULLIF(TRIM(custom_attributes), ''), '{}'),
      '$.description', COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(custom_attributes, '$.description')), ''),
        CONCAT(name, ' là thiết bị cho thuê đã được AMY kiểm tra và chuẩn bị trước mỗi lịch thuê. Thông số và phụ kiện bàn giao được đối chiếu trực tiếp khi nhận máy.')
      ),
      '$.usageGuide', COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(custom_attributes, '$.usageGuide')), ''),
        CONCAT('Kiểm tra pin, thẻ nhớ và phụ kiện của ', name, ' trước khi sử dụng. Bật máy, chọn chế độ chụp hoặc quay phù hợp và tắt nguồn trước khi tháo pin hay thẻ nhớ.')
      ),
      '$.connectionGuide', COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(custom_attributes, '$.connectionGuide')), ''),
        CONCAT('Sử dụng đúng cáp, đầu đọc thẻ nhớ hoặc kết nối không dây được hỗ trợ bởi ', name, '. Không tháo thiết bị khi dữ liệu đang được truyền.')
      )
    )
WHERE level_code = 'L1' AND active = TRUE;
