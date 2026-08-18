-- The business no longer applies a fee for handing equipment to another person.
-- Keep the legacy column for API/schema compatibility, but clear all configured values.
UPDATE products
SET unauthorized_transfer_fee = 0;
