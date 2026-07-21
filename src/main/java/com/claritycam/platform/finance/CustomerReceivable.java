package com.claritycam.platform.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_receivables")
public class CustomerReceivable {
  @Id private String id;
  private String bookingId;
  private String state;
  private BigDecimal originalAmount;
  private BigDecimal outstandingAmount;
  private LocalDateTime createdAt;
  private LocalDateTime formalizedAt;
  private String createdBy;

  protected CustomerReceivable() {}

  public CustomerReceivable(String bookingId, BigDecimal amount, String actor) {
    this.id = "REC-" + CommercialSnapshotLine.compactId();
    this.bookingId = bookingId;
    this.state = "OPEN";
    this.originalAmount = CommercialSnapshot.amount(amount);
    this.outstandingAmount = this.originalAmount;
    this.createdAt = LocalDateTime.now();
    this.createdBy = actor;
  }
  public void formalize() { this.state = "FORMALIZED"; this.formalizedAt = LocalDateTime.now(); }
  public void collect(BigDecimal amount) {
    this.outstandingAmount = getOutstandingAmount().subtract(CommercialSnapshot.amount(amount)).max(BigDecimal.ZERO);
    if (this.outstandingAmount.signum() == 0) this.state = "COLLECTED";
  }
  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getState() { return state; }
  public BigDecimal getOriginalAmount() { return originalAmount; }
  public BigDecimal getOutstandingAmount() { return outstandingAmount == null ? BigDecimal.ZERO : outstandingAmount; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getFormalizedAt() { return formalizedAt; }
  public String getCreatedBy() { return createdBy; }
}
