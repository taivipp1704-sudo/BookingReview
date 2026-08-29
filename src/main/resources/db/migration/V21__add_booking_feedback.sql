-- Đánh giá của khách sau khi đơn thuê đã hoàn tất: số sao 1-5 và nhận xét tự do
-- (không bắt buộc). Mỗi đơn chỉ giữ một bản ghi đánh giá; gửi lại sẽ ghi đè bản
-- ghi cũ thay vì tạo bản ghi trùng (ràng buộc unique trên booking_id).
CREATE TABLE IF NOT EXISTS booking_feedback (
  id VARCHAR(64) NOT NULL,
  booking_id VARCHAR(64) NOT NULL,
  phone_normalized VARCHAR(20) NOT NULL,
  rating INT NOT NULL,
  comment VARCHAR(2000) NOT NULL DEFAULT '',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_booking_feedback_booking (booking_id),
  CONSTRAINT fk_booking_feedback_booking
    FOREIGN KEY (booking_id) REFERENCES bookings(id),
  CONSTRAINT chk_booking_feedback_rating CHECK (rating BETWEEN 1 AND 5),
  INDEX idx_booking_feedback_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
