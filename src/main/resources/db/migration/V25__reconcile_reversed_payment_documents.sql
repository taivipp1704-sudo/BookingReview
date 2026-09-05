-- Đồng bộ các khoản thu cũ với chứng từ phiếu thu đã bị đảo. Từ V25 trở đi,
-- correlation_id của PAYMENT_RECEIPT lưu trực tiếp payment_id; điều kiện theo
-- idempotency_key giữ khả năng sửa dữ liệu được tạo trước thay đổi đó.
UPDATE payments p
JOIN financial_documents d
  ON d.type = 'PAYMENT_RECEIPT'
 AND d.status = 'REVERSED'
 AND (
      d.correlation_id = p.id
      OR d.idempotency_key = CONCAT('document:', p.idempotency_key)
 )
SET p.status = 'REVERSED'
WHERE p.status = 'SUCCEEDED';
