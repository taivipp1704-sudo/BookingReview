package com.claritycam.platform.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_documents")
public class FinancialDocument {
  @Id private String id;
  private String bookingId;
  private String type;
  private String status;
  private String currency;
  private String correlationId;
  @Column(unique = true, length = 160) private String idempotencyKey;
  private BigDecimal totalDebit;
  private BigDecimal totalCredit;
  private LocalDateTime effectiveAt;
  private LocalDateTime postedAt;
  private String createdBy;
  private String approvedBy;
  private String reversalOfDocumentId;
  @Column(length = 500) private String reason;

  protected FinancialDocument() {}

  public FinancialDocument(String id, String bookingId, String type, BigDecimal amount, String correlationId,
      String idempotencyKey, String actor, String reason) {
    this.id = id;
    this.bookingId = bookingId;
    this.type = type;
    this.status = "POSTED";
    this.currency = "VND";
    this.correlationId = correlationId;
    this.idempotencyKey = idempotencyKey;
    this.totalDebit = CommercialSnapshot.amount(amount);
    this.totalCredit = CommercialSnapshot.amount(amount);
    this.effectiveAt = LocalDateTime.now();
    this.postedAt = this.effectiveAt;
    this.createdBy = actor;
    this.approvedBy = actor;
    this.reason = reason;
  }

  public void reverse(String actor) { this.status = "REVERSED"; this.approvedBy = actor; }
  public void linkReversal(String originalId) { this.reversalOfDocumentId = originalId; }
  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getType() { return type; }
  public String getStatus() { return status; }
  public String getCurrency() { return currency; }
  public String getCorrelationId() { return correlationId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public BigDecimal getTotalDebit() { return totalDebit; }
  public BigDecimal getTotalCredit() { return totalCredit; }
  public LocalDateTime getEffectiveAt() { return effectiveAt; }
  public LocalDateTime getPostedAt() { return postedAt; }
  public String getCreatedBy() { return createdBy; }
  public String getApprovedBy() { return approvedBy; }
  public String getReversalOfDocumentId() { return reversalOfDocumentId; }
  public String getReason() { return reason; }
}
