-- Explicitly requested clean-slate reset for end-to-end testing.
-- Preserve accounts, products, store branches, physical inventory and stock totals.

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

-- A booking reset must not leave stock or serialized equipment marked as rented.
UPDATE stock_items SET in_use_qty = 0;
UPDATE inventory_assets
SET status = 'AVAILABLE', last_check = CURRENT_DATE
WHERE status = 'IN_USE';
