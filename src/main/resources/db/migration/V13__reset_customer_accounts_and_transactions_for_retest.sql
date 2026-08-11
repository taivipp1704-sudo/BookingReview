-- Fresh-start reset for a new end-to-end test cycle.
-- Preserve admin users, approved catalog, physical stock quantities and store branches.

-- Remove all financial and booking activity created during previous test runs.
DELETE FROM payment_allocations;
DELETE FROM payments;
DELETE FROM booking_charges;
DELETE FROM refund_requests;
DELETE FROM booking_settlements;
DELETE FROM customer_receivables;
DELETE FROM asset_cost_allocations;
DELETE FROM asset_revenue_allocations;
DELETE FROM commercial_snapshot_lines;
DELETE FROM commercial_snapshots;
DELETE FROM financial_ledger_entries;
DELETE FROM financial_documents;
DELETE FROM financial_reconciliation_findings;
DELETE FROM finance_outbox_events;
DELETE FROM operational_expenses;
DELETE FROM financial_periods;

DELETE FROM booking_allocations;
DELETE FROM booking_reservations;
DELETE FROM booking_lines;
DELETE FROM bookings;

-- Remove customer-facing history, uploaded-document records, OTP state and request limits.
DELETE FROM customer_support_requests;
DELETE FROM customer_waitlist;
UPDATE identity_uploads
SET expires_at = '2000-01-01 00:00:00',
    consumed_at = NULL;
DELETE FROM otp_challenges;
DELETE FROM customer_accounts;
DELETE FROM api_rate_limit_buckets;

-- A clean test cycle has no previous movement or audit records.
DELETE FROM inventory_ledger_entries;
DELETE FROM audit_logs;

-- Bookings must not leave equipment or bulk stock reserved after the reset.
UPDATE stock_items SET in_use_qty = 0;
UPDATE inventory_assets
SET status = 'AVAILABLE', last_check = CURRENT_DATE
WHERE status = 'IN_USE';
