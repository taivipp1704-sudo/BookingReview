package com.claritycam.platform.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "operational_expenses")
public class OperationalExpense {
  @Id private String id;
  private String bookingId;
  private String assetId;
  private String branchId;
  private String category;
  private String state;
  private BigDecimal amount;
  private BigDecimal paidAmount;
  private String vendorName;
  private String invoiceReference;
  @Column(unique = true, length = 160) private String sourceFingerprint;
  @Column(length = 500) private String reason;
  @Column(length = 1000) private String evidenceReference;
  private LocalDateTime createdAt;
  private LocalDateTime approvedAt;
  private String createdBy;
  private String approvedBy;
  @Version private long version;

  protected OperationalExpense() {}

  public OperationalExpense(String bookingId, String assetId, String branchId, String category, BigDecimal amount,
      String vendorName, String invoiceReference, String sourceFingerprint, String reason,
      String evidenceReference, String actor) {
    this.id = "EXP-" + CommercialSnapshotLine.compactId();
    this.bookingId = bookingId;
    this.assetId = assetId;
    this.branchId = branchId;
    this.category = category;
    this.state = "SUBMITTED";
    this.amount = CommercialSnapshot.amount(amount);
    this.paidAmount = BigDecimal.ZERO;
    this.vendorName = vendorName;
    this.invoiceReference = invoiceReference;
    this.sourceFingerprint = sourceFingerprint;
    this.reason = reason;
    this.evidenceReference = evidenceReference;
    this.createdAt = LocalDateTime.now();
    this.createdBy = actor;
  }

  public void approve(String actor) {
    this.state = "APPROVED";
    this.approvedAt = LocalDateTime.now();
    this.approvedBy = actor;
  }

  public void pay(BigDecimal paid) {
    this.paidAmount = getPaidAmount().add(CommercialSnapshot.amount(paid));
    this.state = this.paidAmount.compareTo(getAmount()) >= 0 ? "PAID" : "PARTIALLY_PAID";
  }

  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getAssetId() { return assetId; }
  public String getBranchId() { return branchId; }
  public String getCategory() { return category; }
  public String getState() { return state; }
  public BigDecimal getAmount() { return CommercialSnapshot.amount(amount); }
  public BigDecimal getPaidAmount() { return CommercialSnapshot.amount(paidAmount); }
  public String getVendorName() { return vendorName; }
  public String getInvoiceReference() { return invoiceReference; }
  public String getSourceFingerprint() { return sourceFingerprint; }
  public String getReason() { return reason; }
  public String getEvidenceReference() { return evidenceReference; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getApprovedAt() { return approvedAt; }
  public String getCreatedBy() { return createdBy; }
  public String getApprovedBy() { return approvedBy; }
  public long getVersion() { return version; }
}
