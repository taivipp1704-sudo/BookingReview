package com.claritycam.platform.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {
  @Id private String id;
  private String bookingId;
  private String status;
  private BigDecimal amount;
  private BigDecimal allocatedAmount;
  private String method;
  @Column(unique = true, length = 160) private String providerTransactionId;
  @Column(unique = true, length = 160) private String idempotencyKey;
  private LocalDateTime receivedAt;
  private String recordedBy;
  @Column(length = 500) private String note;

  protected Payment() {}

  public Payment(String id, String bookingId, BigDecimal amount, String method, String providerTransactionId,
      String idempotencyKey, String actor, String note) {
    this.id = id;
    this.bookingId = bookingId;
    this.status = "SUCCEEDED";
    this.amount = CommercialSnapshot.amount(amount);
    this.allocatedAmount = BigDecimal.ZERO;
    this.method = method;
    this.providerTransactionId = providerTransactionId;
    this.idempotencyKey = idempotencyKey;
    this.receivedAt = LocalDateTime.now();
    this.recordedBy = actor;
    this.note = note;
  }

  public void allocate(BigDecimal value) {
    BigDecimal next = getAllocatedAmount().add(CommercialSnapshot.amount(value));
    if (next.compareTo(amount) > 0) throw new IllegalArgumentException("Allocation exceeds payment amount");
    this.allocatedAmount = next;
  }

  /**
   * Đánh dấu khoản thu này đã bị đảo (ví dụ admin ghi nhận nhầm số tiền và đã đảo
   * chứng từ kế toán tương ứng). Không xoá bản ghi gốc để giữ lịch sử — chỉ đổi
   * trạng thái để các phép tính "đã thực nhận"/"còn phải thu" loại khoản này ra.
   */
  public void markReversed() {
    this.status = "REVERSED";
  }

  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getStatus() { return status; }
  public BigDecimal getAmount() { return amount; }
  public BigDecimal getAllocatedAmount() { return allocatedAmount == null ? BigDecimal.ZERO : allocatedAmount; }
  public BigDecimal getAvailableAmount() { return amount.subtract(getAllocatedAmount()); }
  public String getMethod() { return method; }
  public String getProviderTransactionId() { return providerTransactionId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public LocalDateTime getReceivedAt() { return receivedAt; }
  public String getRecordedBy() { return recordedBy; }
  public String getNote() { return note; }
}
