ALTER TABLE customer_accounts
  ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100) NULL;

CREATE TABLE IF NOT EXISTS api_rate_limit_buckets (
  key_hash CHAR(64) NOT NULL,
  hits INT NOT NULL DEFAULT 0,
  window_started_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (key_hash),
  INDEX idx_rate_limit_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

UPDATE products
SET active = TRUE,
    level_code = 'L1'
WHERE id LIKE 'CAM-%'
  AND custom_attributes LIKE '%AMY_DIGITAL_Tong_hop_may_phu_kien_gia_den_bu.xlsx%';
