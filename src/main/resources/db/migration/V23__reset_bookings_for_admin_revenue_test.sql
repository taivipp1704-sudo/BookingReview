-- Xoá toàn bộ đơn hàng (booking) và dữ liệu tài chính liên quan đã tạo trong
-- quá trình test trước đó, để admin tự đặt đơn mới và tính lại doanh thu từ
-- đầu cho sạch. Giữ nguyên tài khoản khách, catalog sản phẩm/combo, chi
-- nhánh và tồn kho — chỉ xoá dữ liệu phát sinh từ việc đặt đơn.

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

DELETE FROM booking_feedback;
DELETE FROM booking_allocations;
DELETE FROM booking_reservations;
DELETE FROM booking_lines;
DELETE FROM bookings;

-- Xoá đơn xong không được để lại tồn kho/máy bị đánh dấu đang cho thuê.
UPDATE stock_items SET in_use_qty = 0;
UPDATE inventory_assets
SET status = 'AVAILABLE', last_check = CURRENT_DATE
WHERE status = 'IN_USE';
