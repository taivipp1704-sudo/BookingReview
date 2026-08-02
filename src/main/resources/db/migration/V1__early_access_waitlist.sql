CREATE TABLE IF NOT EXISTS customer_accounts (
  id VARCHAR(64) NOT NULL,
  phone_normalized VARCHAR(20) NOT NULL,
  name VARCHAR(180) NOT NULL,
  created_at DATETIME(6) NULL,
  last_login_at DATETIME(6) NULL,
  onboarding_version INT NOT NULL DEFAULT 0,
  onboarding_completed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_customer_accounts_phone UNIQUE (phone_normalized)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_waitlist (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id VARCHAR(64) NOT NULL,
  phone_normalized VARCHAR(20) NOT NULL,
  name VARCHAR(180) NOT NULL,
  status VARCHAR(24) NOT NULL,
  source VARCHAR(40) NOT NULL,
  consent_version VARCHAR(32) NOT NULL,
  consented_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  admin_note VARCHAR(1000) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_customer_waitlist_phone UNIQUE (phone_normalized),
  CONSTRAINT uk_customer_waitlist_account UNIQUE (account_id),
  CONSTRAINT fk_customer_waitlist_account FOREIGN KEY (account_id) REFERENCES customer_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS imported_reference_records (
  id VARCHAR(160) NOT NULL,
  category VARCHAR(40) NOT NULL,
  label VARCHAR(180) NOT NULL,
  review_status VARCHAR(24) NOT NULL,
  source_file VARCHAR(255) NOT NULL,
  payload LONGTEXT NOT NULL,
  imported_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_imported_reference_category_status (category, review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
