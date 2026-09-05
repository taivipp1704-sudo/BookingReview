-- Sửa dữ liệu bị kẹt cho đơn ORD-75C8DD0D: admin ghi nhận nhầm khoản thu
-- 1.140.750đ và đã đảo chứng từ kế toán (DOC-C9EB2AD7F0274A36 đảo
-- DOC-F8E1F2A43F1A4DB9) TRƯỚC KHI có bản vá Payment.markReversed() trong
-- FinanceSettlementService.reverseDocument. Vì vậy khoản thu gốc trong bảng
-- payments vẫn đang ở trạng thái SUCCEEDED, khiến "Đã thực nhận"/"Còn phải thu"
-- của đơn này tính sai và ô ghi nhận tiền không hiện lại. Đánh dấu thủ công
-- đúng khoản thu đó là REVERSED một lần duy nhất cho đơn này.
UPDATE payments
SET status = 'REVERSED'
WHERE booking_id = 'ORD-75C8DD0D' AND status = 'SUCCEEDED' AND amount = 1140750;
