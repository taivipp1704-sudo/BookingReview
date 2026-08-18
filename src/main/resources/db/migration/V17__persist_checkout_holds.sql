CREATE TABLE IF NOT EXISTS checkout_holds (
  token VARCHAR(64) NOT NULL,
  pickup_time DATETIME(6) NOT NULL,
  return_time DATETIME(6) NOT NULL,
  bundle_id VARCHAR(64) NULL,
  promotion_code VARCHAR(64) NULL,
  rental_rate VARCHAR(24) NULL,
  owner_phone VARCHAR(20) NOT NULL,
  identity_upload_token VARCHAR(64) NULL,
  payment_proof_upload_token VARCHAR(64) NULL,
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (token),
  INDEX idx_checkout_holds_owner_expiry (owner_phone, expires_at),
  INDEX idx_checkout_holds_period (expires_at, pickup_time, return_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS checkout_hold_items (
  hold_token VARCHAR(64) NOT NULL,
  product_id VARCHAR(64) NOT NULL,
  quantity INT NOT NULL,
  PRIMARY KEY (hold_token, product_id),
  CONSTRAINT fk_checkout_hold_items_hold
    FOREIGN KEY (hold_token) REFERENCES checkout_holds(token) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
