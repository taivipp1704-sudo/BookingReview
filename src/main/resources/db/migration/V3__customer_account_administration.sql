ALTER TABLE customer_accounts
  ADD COLUMN email VARCHAR(255) NULL,
  ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE customer_accounts
  ADD CONSTRAINT uk_customer_accounts_email UNIQUE (email);
