package com.claritycam.platform.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_requests")
public class RefundRequest {
  @Id private String id;
  private String bookingId;
  private String state;
  private BigDecimal amount;
  private String method;
  @Column(unique = true, length = 160) private String idempotencyKey;
  private String payoutReference;
  private LocalDateTime requestedAt;
  private LocalDateTime approvedAt;
  private LocalDateTime executedAt;
  private String approvedBy;
  @Version private long version;

  protected RefundRequest() {}

  public RefundRequest(String bookingId, BigDecimal amount, String method, String idempotencyKey, String actor) {
    this.id = "REF-" + CommercialSnapshotLine.compactId();
    this.bookingId = bookingId;
    this.state = "APPROVED";
    this.amount = CommercialSnapshot.amount(amount);
    this.method = method;
    this.idempotencyKey = idempotencyKey;
    this.requestedAt = LocalDateTime.now();
    this.approvedAt = this.requestedAt;
    this.approvedBy = actor;
  }

  public void succeed(String payoutReference) {
    this.state = "SUCCEEDED";
    this.payoutReference = payoutReference;
    this.executedAt = LocalDateTime.now();
  }
  public void fail() { this.state = "FAILED"; }
  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getState() { return state; }
  public BigDecimal getAmount() { return amount; }
  public String getMethod() { return method; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getPayoutReference() { return payoutReference; }
  public LocalDateTime getRequestedAt() { return requestedAt; }
  public LocalDateTime getApprovedAt() { return approvedAt; }
  public LocalDateTime getExecutedAt() { return executedAt; }
  public String getApprovedBy() { return approvedBy; }
  public long getVersion() { return version; }
}
