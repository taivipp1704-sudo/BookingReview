-- Mã PIN 6 số giúp khách thuê không thường xuyên đăng nhập nhanh mà không cần
-- nhớ mật khẩu đầy đủ. Mật khẩu gốc vẫn được giữ nguyên và tiếp tục hoạt động;
-- PIN chỉ là phương thức đăng nhập nhanh thứ hai, khách tự đặt/đổi sau khi xác
-- thực bằng mật khẩu. pin_failed_attempts đếm số lần nhập sai liên tiếp để tự
-- vô hiệu hoá PIN khi bị dò mã (xem CustomerAccount.registerPinFailure).
ALTER TABLE customer_accounts
  ADD COLUMN pin_hash VARCHAR(100) NULL,
  ADD COLUMN pin_failed_attempts INT NOT NULL DEFAULT 0;
