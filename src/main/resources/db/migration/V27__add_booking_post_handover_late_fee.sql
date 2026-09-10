-- Phí trả trễ ghi nhận trực tiếp SAU khi đã bàn giao máy (không cần khách
-- yêu cầu trước, admin nhập phí + lý do rồi duyệt 1 bước, giống phần "Nhận
-- máy sớm") — tách khỏi late_return_fee (là phí thương lượng TRƯỚC khi bàn
-- giao dựa trên yêu cầu của khách). Xem Booking.applyPostHandoverLateFee()
-- và BookingService.applyPostHandoverLateFee().
ALTER TABLE bookings
  ADD COLUMN post_handover_late_fee DECIMAL(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN post_handover_late_fee_reason VARCHAR(500) NULL;
