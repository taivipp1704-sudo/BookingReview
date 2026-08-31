-- Phụ phí trả máy trễ hơn gói đã chọn (ví dụ khách thuê 1,5 / 2,5 ngày): khách
-- tick yêu cầu ngay trong form đặt thuê và chọn giờ trả mong muốn, admin vào đơn
-- duyệt và điền số tiền phụ phí — cùng cơ chế với yêu cầu nhận máy sớm
-- (early_pickup_*) đã có. Không tự tính phí vì đa số khách đã nhắn tin thoả
-- thuận mức phí này qua page trước khi đặt (xem Booking.reviewLateReturn).
ALTER TABLE bookings
  ADD COLUMN late_return_requested BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN late_return_time DATETIME(6) NULL,
  ADD COLUMN late_return_approved BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN late_return_fee DECIMAL(19,2) NOT NULL DEFAULT 0;
