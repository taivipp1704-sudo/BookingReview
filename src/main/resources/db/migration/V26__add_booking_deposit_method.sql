-- Phương án cọc mới: thay vì cọc máy 500k bằng tiền mặt, khách có thể chọn
-- cọc bằng 2 giấy tờ (CCCD/Bằng lái xe/Passport, ít nhất 1 cái của người đặt)
-- + link FB/IG public chính chủ. Khi chọn DOCUMENTS, equipmentDeposit được
-- tính về 0đ ở BookingService.submit() — xem Booking.applyDepositMethod().
-- (Đổi từ V25 -> V26 vì trùng version với V25__reconcile_reversed_payment_documents.sql
-- đã có sẵn trên máy, gây lỗi Flyway "Found more than one migration with version 25".)
ALTER TABLE bookings
  ADD COLUMN deposit_method VARCHAR(20) NOT NULL DEFAULT 'CASH',
  ADD COLUMN secondary_identity_type VARCHAR(30) NULL,
  ADD COLUMN secondary_identity_front_reference VARCHAR(255) NULL,
  ADD COLUMN secondary_identity_back_reference VARCHAR(255) NULL,
  ADD COLUMN social_profile_link VARCHAR(500) NULL;
