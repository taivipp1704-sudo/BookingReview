-- Ảnh tài khoản ngân hàng của khách, dùng để hoàn tiền cọc giữ lịch mà không phải
-- nhắn tin hỏi lại từng khách. Lưu cùng cơ chế mã hoá với CCCD và ảnh chuyển khoản:
-- cột này chỉ giữ storage key, không giữ số tài khoản dạng văn bản.
ALTER TABLE bookings
  ADD COLUMN bank_account_reference VARCHAR(255) NULL;
