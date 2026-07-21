package com.claritycam.platform.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_charges")
public class BookingCharge {
  @Id private String id;
  private String bookingId;
  private String assetId;
  private String type;
  private String status;
  private BigDecimal proposedAmount;
  private BigDecimal confirmedAmount;
  private BigDecimal temporaryHoldAmount;
  private String ruleVersion;
  @Column(length = 500) private String reason;
  @Column(length = 1000) private String evidenceReference;
  private LocalDateTime expectedResolutionAt;
  private LocalDateTime createdAt;
  private String createdBy;
  private LocalDateTime reviewedAt;
  private String reviewedBy;

  protected BookingCharge() {}

  public BookingCharge(String bookingId, String assetId, String type, BigDecimal amount, BigDecimal holdAmount,
      String reason, String evidenceReference, LocalDateTime expectedResolutionAt, String actor) {
    this.id = "CHG-" + CommercialSnapshotLine.compactId();
    this.bookingId = bookingId;
    this.assetId = assetId;
    this.type = type;
    this.status = "PROPOSED";
    this.proposedAmount = CommercialSnapshot.amount(amount);
    this.confirmedAmount = BigDecimal.ZERO;
    this.temporaryHoldAmount = CommercialSnapshot.amount(holdAmount);
    this.ruleVersion = "CHARGE_V1";
    this.reason = reason;
    this.evidenceReference = evidenceReference;
    this.expectedResolutionAt = expectedResolutionAt;
    this.createdAt = LocalDateTime.now();
    this.createdBy = actor;
  }

  public void review(boolean approved, BigDecimal amount, String actor, String reason) {
    this.status = approved ? "CONFIRMED" : "CANCELLED";
    this.confirmedAmount = approved ? CommercialSnapshot.amount(amount == null ? proposedAmount : amount) : BigDecimal.ZERO;
    this.temporaryHoldAmount = BigDecimal.ZERO;
    this.reviewedAt = LocalDateTime.now();
    this.reviewedBy = actor;
    if (reason != null && !reason.isBlank()) this.reason = reason.trim();
  }

  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getAssetId() { return assetId; }
  public String getType() { return type; }
  public String getStatus() { return status; }
  public BigDecimal getProposedAmount() { return CommercialSnapshot.amount(proposedAmount); }
  public BigDecimal getConfirmedAmount() { return CommercialSnapshot.amount(confirmedAmount); }
  public BigDecimal getTemporaryHoldAmount() { return CommercialSnapshot.amount(temporaryHoldAmount); }
  public String getRuleVersion() { return ruleVersion; }
  public String getReason() { return reason; }
  public String getEvidenceReference() { return evidenceReference; }
  public LocalDateTime getExpectedResolutionAt() { return expectedResolutionAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public String getCreatedBy() { return createdBy; }
  public LocalDateTime getReviewedAt() { return reviewedAt; }
  public String getReviewedBy() { return reviewedBy; }
}
