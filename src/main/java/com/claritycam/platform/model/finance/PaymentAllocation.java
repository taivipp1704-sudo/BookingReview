package com.claritycam.platform.model.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation {
  @Id private String id;
  private String paymentId;
  private String bookingId;
  private String obligationType;
  private String obligationId;
  private BigDecimal amount;
  private LocalDateTime allocatedAt;
  private String allocatedBy;

  protected PaymentAllocation() {}

  public PaymentAllocation(String paymentId, String bookingId, String obligationType, String obligationId,
      BigDecimal amount, String actor) {
    this.id = "PAL-" + CommercialSnapshotLine.compactId();
    this.paymentId = paymentId;
    this.bookingId = bookingId;
    this.obligationType = obligationType;
    this.obligationId = obligationId;
    this.amount = CommercialSnapshot.amount(amount);
    this.allocatedAt = LocalDateTime.now();
    this.allocatedBy = actor;
  }

  public String getId() { return id; }
  public String getPaymentId() { return paymentId; }
  public String getBookingId() { return bookingId; }
  public String getObligationType() { return obligationType; }
  public String getObligationId() { return obligationId; }
  public BigDecimal getAmount() { return amount; }
  public LocalDateTime getAllocatedAt() { return allocatedAt; }
  public String getAllocatedBy() { return allocatedBy; }
}
