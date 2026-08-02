package com.claritycam.platform.model.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_settlements")
public class BookingSettlement {
  @Id private String bookingId;
  private String state;
  private boolean revenueRecognized;
  private BigDecimal recognizedRevenue;
  private BigDecimal depositsHeld;
  private BigDecimal confirmedDeductions;
  private BigDecimal temporaryHoldAmount;
  private BigDecimal refundableAmount;
  private BigDecimal refundDueNow;
  private BigDecimal successfulRefundAmount;
  private BigDecimal receivableAmount;
  private LocalDateTime settlementEligibleAt;
  private LocalDateTime approvedAt;
  private LocalDateTime closedAt;
  private String approvedBy;
  @Version private long version;

  protected BookingSettlement() {}

  public BookingSettlement(String bookingId) {
    this.bookingId = bookingId;
    this.state = "DRAFT";
    this.recognizedRevenue = BigDecimal.ZERO;
    this.depositsHeld = BigDecimal.ZERO;
    this.confirmedDeductions = BigDecimal.ZERO;
    this.temporaryHoldAmount = BigDecimal.ZERO;
    this.refundableAmount = BigDecimal.ZERO;
    this.refundDueNow = BigDecimal.ZERO;
    this.successfulRefundAmount = BigDecimal.ZERO;
    this.receivableAmount = BigDecimal.ZERO;
  }

  public void calculate(BigDecimal revenue, BigDecimal deposits, BigDecimal deductions, BigDecimal hold,
      BigDecimal successfulRefund) {
    this.revenueRecognized = true;
    this.recognizedRevenue = CommercialSnapshot.amount(revenue);
    this.depositsHeld = CommercialSnapshot.amount(deposits);
    this.confirmedDeductions = CommercialSnapshot.amount(deductions);
    this.temporaryHoldAmount = CommercialSnapshot.amount(hold);
    this.successfulRefundAmount = CommercialSnapshot.amount(successfulRefund);
    BigDecimal grossRefundable = getDepositsHeld().subtract(getConfirmedDeductions()).max(BigDecimal.ZERO);
    this.refundableAmount = grossRefundable.subtract(getSuccessfulRefundAmount()).max(BigDecimal.ZERO);
    this.refundDueNow = this.refundableAmount.subtract(getTemporaryHoldAmount()).max(BigDecimal.ZERO);
    this.receivableAmount = getConfirmedDeductions().subtract(getDepositsHeld()).max(BigDecimal.ZERO);
    this.settlementEligibleAt = LocalDateTime.now();
    this.state = getTemporaryHoldAmount().signum() > 0 ? "ON_HOLD" : "READY";
  }

  public void approve(String actor) {
    this.approvedBy = actor;
    this.approvedAt = LocalDateTime.now();
    this.state = getRefundDueNow().signum() > 0 ? "REFUND_PENDING" : "APPROVED";
  }
  public void markRefunded(BigDecimal amount) {
    this.successfulRefundAmount = getSuccessfulRefundAmount().add(CommercialSnapshot.amount(amount));
    this.refundableAmount = getRefundableAmount().subtract(CommercialSnapshot.amount(amount)).max(BigDecimal.ZERO);
    this.refundDueNow = getRefundDueNow().subtract(CommercialSnapshot.amount(amount)).max(BigDecimal.ZERO);
    this.state = this.refundDueNow.signum() > 0 ? "REFUND_PENDING" : "APPROVED";
  }
  public void close() { this.state = "CLOSED"; this.closedAt = LocalDateTime.now(); }
  public void reopen() { this.state = "REOPENED"; this.closedAt = null; }
  public String getBookingId() { return bookingId; }
  public String getState() { return state; }
  public boolean isRevenueRecognized() { return revenueRecognized; }
  public BigDecimal getRecognizedRevenue() { return CommercialSnapshot.amount(recognizedRevenue); }
  public BigDecimal getDepositsHeld() { return CommercialSnapshot.amount(depositsHeld); }
  public BigDecimal getConfirmedDeductions() { return CommercialSnapshot.amount(confirmedDeductions); }
  public BigDecimal getTemporaryHoldAmount() { return CommercialSnapshot.amount(temporaryHoldAmount); }
  public BigDecimal getRefundableAmount() { return CommercialSnapshot.amount(refundableAmount); }
  public BigDecimal getRefundDueNow() { return CommercialSnapshot.amount(refundDueNow); }
  public BigDecimal getSuccessfulRefundAmount() { return CommercialSnapshot.amount(successfulRefundAmount); }
  public BigDecimal getReceivableAmount() { return CommercialSnapshot.amount(receivableAmount); }
  public LocalDateTime getSettlementEligibleAt() { return settlementEligibleAt; }
  public LocalDateTime getApprovedAt() { return approvedAt; }
  public LocalDateTime getClosedAt() { return closedAt; }
  public String getApprovedBy() { return approvedBy; }
  public long getVersion() { return version; }
}
