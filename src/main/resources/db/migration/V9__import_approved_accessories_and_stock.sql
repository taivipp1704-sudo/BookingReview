-- Approved accessory and spare-part baseline from
-- AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx / 03_Tong_nhu_cau_phu_kien.
-- These are included kit components, so rental prices remain zero. The damage
-- liability limit carries the proposed compensation amount for admin reference.

INSERT INTO products (
  id, level_code, name, brand, category,
  hourly_price, half_day_price, daily_price, two_day_price, multi_day_price,
  multi_day_days, extra_day_price, equipment_deposit, booking_deposit,
  late_fee_per_hour, identity_violation_fee, unauthorized_transfer_fee,
  impact_penalty_percent, damage_liability_limit,
  included, active, image_url, specs, tracking_mode, serial_prefix,
  store_branch_id, booking_count_base, custom_attributes
) VALUES
  ('ACC-BAT-LPE17', 'L2', 'LP-E17', 'Canon', 'Pin', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 700000, TRUE, TRUE, '', 'Pin Canon LP-E17', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Pin","requiredQty":12,"bufferQty":3,"totalSuggestedQty":15,"usedWith":"Canon R50 (5), Canon M6 (1)","notes":"2 pin/bộ + dự phòng kho","compensationRef":"BAT-LPE17-COMP"}'),
  ('ACC-BAT-LPE12', 'L2', 'LP-E12', 'Canon', 'Pin', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 450000, TRUE, TRUE, '', 'Pin Canon LP-E12', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Pin","requiredQty":12,"bufferQty":3,"totalSuggestedQty":15,"usedWith":"Canon M50, M100 (3), M200, M10","notes":"2 pin/bộ + dự phòng kho","compensationRef":"BAT-LPE12"}'),
  ('ACC-BAT-NB13L', 'L2', 'NB-13L', 'Canon', 'Pin', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 700000, TRUE, TRUE, '', 'Pin Canon NB-13L', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Pin","requiredQty":2,"bufferQty":1,"totalSuggestedQty":3,"usedWith":"Canon G7X M2","notes":"2 pin/bộ + 1 dự phòng","compensationRef":"BAT-NB13L"}'),
  ('ACC-BAT-NPW126S', 'L2', 'NP-W126S', 'Fujifilm', 'Pin', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 450000, TRUE, TRUE, '', 'Pin Fujifilm NP-W126S', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Pin","requiredQty":4,"bufferQty":1,"totalSuggestedQty":5,"usedWith":"Fuji XA5, Fuji XM5","notes":"2 pin/bộ + 1 dự phòng","compensationRef":"BAT-NPW126S-COMP"}'),
  ('ACC-BAT-NB4L', 'L2', 'NB-4L', 'Canon', 'Pin', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 300000, TRUE, TRUE, '', 'Pin Canon NB-4L', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Pin","requiredQty":2,"bufferQty":1,"totalSuggestedQty":3,"usedWith":"Canon IXY 600F","notes":"Cần xác minh trực tiếp trước khi mua","compensationRef":"BAT-NB4L"}'),
  ('ACC-BAT-NB11LH', 'L2', 'NB-11LH', 'Canon', 'Pin', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 350000, TRUE, TRUE, '', 'Pin Canon NB-11LH', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Pin","requiredQty":2,"bufferQty":1,"totalSuggestedQty":3,"usedWith":"Canon IXY 650","notes":"2 pin/bộ + 1 dự phòng","compensationRef":"BAT-NB11LH"}'),
  ('ACC-POWER-POCKET3', 'L2', 'Battery Handle / pin USB-C', 'DJI', 'Nguồn dự phòng', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 2100000, TRUE, TRUE, '', 'Nguồn dự phòng cho Pocket 3', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Nguồn dự phòng","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Pocket 3","notes":"Thay cho pin rời thứ hai","compensationRef":"POCKET3-BAT-HANDLE"}'),
  ('ACC-CHG-LCE17', 'L2', 'LC-E17', 'Canon', 'Sạc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 300000, TRUE, TRUE, '', 'Sạc Canon LP-E17', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Sạc","requiredQty":6,"bufferQty":1,"totalSuggestedQty":7,"usedWith":"Canon R50, Canon M6","notes":"1 sạc/bộ + 1 dự phòng chung","compensationRef":"CHG-LPE17"}'),
  ('ACC-CHG-LCE12', 'L2', 'LC-E12', 'Canon', 'Sạc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 250000, TRUE, TRUE, '', 'Sạc Canon LP-E12', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Sạc","requiredQty":6,"bufferQty":1,"totalSuggestedQty":7,"usedWith":"Canon M50, M100, M200, M10","notes":"1 sạc/bộ + 1 dự phòng chung","compensationRef":"CHG-LPE12"}'),
  ('ACC-CHG-NB13L', 'L2', 'Sạc NB-13L', 'Canon', 'Sạc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 300000, TRUE, TRUE, '', 'Sạc Canon NB-13L', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Sạc","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Canon G7X M2","compensationRef":"CHG-NB13L"}'),
  ('ACC-CHG-NPW126S', 'L2', 'BC-W126S / sạc tương thích', 'Fujifilm', 'Sạc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 300000, TRUE, TRUE, '', 'Sạc Fujifilm NP-W126S', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Sạc","requiredQty":2,"bufferQty":1,"totalSuggestedQty":3,"usedWith":"Fuji XA5, Fuji XM5","compensationRef":"CHG-NPW126S"}'),
  ('ACC-CHG-NB4L', 'L2', 'Sạc NB-4L', 'Canon', 'Sạc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 250000, TRUE, TRUE, '', 'Sạc Canon NB-4L', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Sạc","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Canon IXY 600F","notes":"Cần xác minh","compensationRef":"CHG-NB4L"}'),
  ('ACC-CHG-CB2LFE', 'L2', 'CB-2LF/CB-2LFE', 'Canon', 'Sạc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 300000, TRUE, TRUE, '', 'Sạc Canon NB-11LH', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Sạc","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Canon IXY 650","compensationRef":"CHG-NB11LH"}'),
  ('ACC-CHG-USBC', 'L2', 'Củ sạc USB-C + cáp', 'DJI', 'Sạc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 400000, TRUE, TRUE, '', 'Củ sạc và cáp USB-C cho Pocket 3', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Sạc","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Pocket 3","compensationRef":"POCKET3-CHARGER, POCKET3-CABLE"}'),
  ('ACC-CARD-SD64', 'L3', 'SD 64GB UHS-I/V30', 'Generic', 'Thẻ nhớ', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 650000, TRUE, TRUE, '', 'Thẻ SD 64GB UHS-I V30', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Thẻ nhớ","requiredQty":15,"bufferQty":3,"totalSuggestedQty":18,"usedWith":"Phần lớn máy ảnh","notes":"Có thể chuẩn hóa chung","compensationRef":"CARD-SD64"}'),
  ('ACC-CARD-SD128', 'L3', 'SD 128GB UHS-I V30', 'Generic', 'Thẻ nhớ', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 1200000, TRUE, TRUE, '', 'Thẻ SD 128GB UHS-I V30', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Thẻ nhớ","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Fuji XM5","notes":"Ưu tiên quay video","compensationRef":"CARD-SD128"}'),
  ('ACC-CARD-MICROSD128', 'L3', 'microSD 128GB U3/V30', 'Generic', 'Thẻ nhớ', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 700000, TRUE, TRUE, '', 'Thẻ microSD 128GB U3/V30', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Thẻ nhớ","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Pocket 3","compensationRef":"CARD-MSD128"}'),
  ('ACC-READER-SD', 'L4', 'Đầu đọc SD USB-C/USB-A', 'Generic', 'Đầu đọc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 450000, TRUE, TRUE, '', 'Đầu đọc thẻ SD USB-C/USB-A', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Đầu đọc","requiredQty":17,"bufferQty":3,"totalSuggestedQty":20,"usedWith":"Tất cả máy ảnh trừ Pocket 3","notes":"Có thể dùng đầu đọc 2 trong 1","compensationRef":"READER-SD"}'),
  ('ACC-READER-MICROSD', 'L4', 'Đầu đọc microSD USB-C/USB-A', 'Generic', 'Đầu đọc', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 350000, TRUE, TRUE, '', 'Đầu đọc thẻ microSD USB-C/USB-A', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Đầu đọc","requiredQty":1,"bufferQty":1,"totalSuggestedQty":2,"usedWith":"Pocket 3","notes":"Có thể dùng đầu đọc 2 trong 1","compensationRef":"READER-MSD"}'),
  ('ACC-STRAP', 'L4', 'Dây đeo máy / tay', 'Generic', 'Phụ kiện', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 200000, TRUE, TRUE, '', 'Dây đeo máy hoặc dây đeo tay', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Phụ kiện","requiredQty":18,"bufferQty":3,"totalSuggestedQty":21,"usedWith":"Toàn bộ thiết bị","compensationRef":"STRAP-NECK, STRAP-WRIST"}'),
  ('ACC-SKIN', 'L4', 'Skin / bao bảo vệ', 'Generic', 'Phụ kiện', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 150000, TRUE, TRUE, '', 'Skin hoặc bao bảo vệ thiết bị', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Phụ kiện","requiredQty":18,"bufferQty":2,"totalSuggestedQty":20,"usedWith":"Toàn bộ thiết bị","compensationRef":"SKIN"}'),
  ('ACC-BAG-KIT', 'L4', 'Túi đựng bộ', 'Generic', 'Phụ kiện', 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 350000, TRUE, TRUE, '', 'Túi đựng bộ thiết bị', 'QUANTITY', '', NULL, 0, '{"source":"AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx","group":"Phụ kiện","requiredQty":18,"bufferQty":2,"totalSuggestedQty":20,"usedWith":"Toàn bộ thiết bị","compensationRef":"BAG-COMPACT, BAG-MIRRORLESS"}')
ON DUPLICATE KEY UPDATE
  level_code = VALUES(level_code),
  name = VALUES(name),
  brand = VALUES(brand),
  category = VALUES(category),
  active = VALUES(active),
  included = VALUES(included),
  specs = VALUES(specs),
  tracking_mode = VALUES(tracking_mode),
  damage_liability_limit = VALUES(damage_liability_limit),
  custom_attributes = VALUES(custom_attributes);

INSERT INTO stock_items (product_id, total_qty, in_use_qty) VALUES
  ('ACC-BAT-LPE17', 15, 0), ('ACC-BAT-LPE12', 15, 0), ('ACC-BAT-NB13L', 3, 0),
  ('ACC-BAT-NPW126S', 5, 0), ('ACC-BAT-NB4L', 3, 0), ('ACC-BAT-NB11LH', 3, 0),
  ('ACC-POWER-POCKET3', 2, 0), ('ACC-CHG-LCE17', 7, 0), ('ACC-CHG-LCE12', 7, 0),
  ('ACC-CHG-NB13L', 2, 0), ('ACC-CHG-NPW126S', 3, 0), ('ACC-CHG-NB4L', 2, 0),
  ('ACC-CHG-CB2LFE', 2, 0), ('ACC-CHG-USBC', 2, 0), ('ACC-CARD-SD64', 18, 0),
  ('ACC-CARD-SD128', 2, 0), ('ACC-CARD-MICROSD128', 2, 0), ('ACC-READER-SD', 20, 0),
  ('ACC-READER-MICROSD', 2, 0), ('ACC-STRAP', 21, 0), ('ACC-SKIN', 20, 0), ('ACC-BAG-KIT', 20, 0)
ON DUPLICATE KEY UPDATE
  total_qty = VALUES(total_qty),
  in_use_qty = 0;

-- Keep user-facing fields ASCII-safe so a legacy SQL connection cannot produce mojibake.
UPDATE products
SET name = CASE id
  WHEN 'ACC-CHG-NB13L' THEN 'Charger NB-13L'
  WHEN 'ACC-CHG-NPW126S' THEN 'BC-W126S compatible charger'
  WHEN 'ACC-CHG-NB4L' THEN 'Charger NB-4L'
  WHEN 'ACC-CHG-USBC' THEN 'USB-C charger and cable'
  WHEN 'ACC-READER-SD' THEN 'SD card reader USB-C/USB-A'
  WHEN 'ACC-READER-MICROSD' THEN 'microSD card reader USB-C/USB-A'
  WHEN 'ACC-STRAP' THEN 'Camera neck or wrist strap'
  WHEN 'ACC-SKIN' THEN 'Protective skin or case'
  WHEN 'ACC-BAG-KIT' THEN 'Camera kit bag'
  ELSE name
END,
category = CASE id
  WHEN 'ACC-POWER-POCKET3' THEN 'Power backup'
  WHEN 'ACC-CHG-LCE17' THEN 'Charger'
  WHEN 'ACC-CHG-LCE12' THEN 'Charger'
  WHEN 'ACC-CHG-NB13L' THEN 'Charger'
  WHEN 'ACC-CHG-NPW126S' THEN 'Charger'
  WHEN 'ACC-CHG-NB4L' THEN 'Charger'
  WHEN 'ACC-CHG-CB2LFE' THEN 'Charger'
  WHEN 'ACC-CHG-USBC' THEN 'Charger'
  WHEN 'ACC-CARD-SD64' THEN 'Memory card'
  WHEN 'ACC-CARD-SD128' THEN 'Memory card'
  WHEN 'ACC-CARD-MICROSD128' THEN 'Memory card'
  WHEN 'ACC-READER-SD' THEN 'Card reader'
  WHEN 'ACC-READER-MICROSD' THEN 'Card reader'
  WHEN 'ACC-STRAP' THEN 'Accessory'
  WHEN 'ACC-SKIN' THEN 'Accessory'
  WHEN 'ACC-BAG-KIT' THEN 'Accessory'
  ELSE category
END,
specs = CASE id
  WHEN 'ACC-POWER-POCKET3' THEN 'Backup power for Pocket 3'
  WHEN 'ACC-CHG-LCE17' THEN 'Canon LP-E17 charger'
  WHEN 'ACC-CHG-LCE12' THEN 'Canon LP-E12 charger'
  WHEN 'ACC-CHG-NB13L' THEN 'Canon NB-13L charger'
  WHEN 'ACC-CHG-NPW126S' THEN 'Fujifilm NP-W126S charger'
  WHEN 'ACC-CHG-NB4L' THEN 'Canon NB-4L charger'
  WHEN 'ACC-CHG-CB2LFE' THEN 'Canon NB-11LH charger'
  WHEN 'ACC-CHG-USBC' THEN 'USB-C charger and cable for Pocket 3'
  WHEN 'ACC-CARD-SD64' THEN 'SD 64GB UHS-I V30 card'
  WHEN 'ACC-CARD-SD128' THEN 'SD 128GB UHS-I V30 card'
  WHEN 'ACC-CARD-MICROSD128' THEN 'microSD 128GB U3 V30 card'
  WHEN 'ACC-READER-SD' THEN 'SD card reader USB-C/USB-A'
  WHEN 'ACC-READER-MICROSD' THEN 'microSD card reader USB-C/USB-A'
  WHEN 'ACC-STRAP' THEN 'Camera neck or wrist strap'
  WHEN 'ACC-SKIN' THEN 'Protective skin or camera case'
  WHEN 'ACC-BAG-KIT' THEN 'Camera kit bag'
  ELSE specs
END
WHERE id IN (
  'ACC-POWER-POCKET3', 'ACC-CHG-LCE17', 'ACC-CHG-LCE12', 'ACC-CHG-NB13L',
  'ACC-CHG-NPW126S', 'ACC-CHG-NB4L', 'ACC-CHG-CB2LFE', 'ACC-CHG-USBC',
  'ACC-CARD-SD64', 'ACC-CARD-SD128', 'ACC-CARD-MICROSD128', 'ACC-READER-SD',
  'ACC-READER-MICROSD', 'ACC-STRAP', 'ACC-SKIN', 'ACC-BAG-KIT'
);
