package com.claritycam.platform.model.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_ledger_entries")
public class FinancialLedgerEntry {
  @Id private String id;
  private String documentId;
  private String bookingId;
  private String accountCode;
  private String direction;
  private BigDecimal amount;
  private String currency;
  private String assetId;
  private String branchId;
  private String correlationId;
  private String idempotencyKey;
  private String reversalOfEntryId;
  private LocalDateTime effectiveAt;
  private LocalDateTime postedAt;
  private String createdBy;

  protected FinancialLedgerEntry() {}

  public FinancialLedgerEntry(String documentId, String bookingId, String accountCode, String direction,
      BigDecimal amount, String assetId, String correlationId, String idempotencyKey, String actor) {
    this.id = "LED-" + CommercialSnapshotLine.compactId();
    this.documentId = documentId;
    this.bookingId = bookingId;
    this.accountCode = accountCode;
    this.direction = direction;
    this.amount = CommercialSnapshot.amount(amount);
    this.currency = "VND";
    this.assetId = assetId;
    this.branchId = "MAIN";
    this.correlationId = correlationId;
    this.idempotencyKey = idempotencyKey;
    this.effectiveAt = LocalDateTime.now();
    this.postedAt = this.effectiveAt;
    this.createdBy = actor;
  }

  public void linkReversal(String originalId) { this.reversalOfEntryId = originalId; }
  public String getId() { return id; }
  public String getDocumentId() { return documentId; }
  public String getBookingId() { return bookingId; }
  public String getAccountCode() { return accountCode; }
  public String getDirection() { return direction; }
  public BigDecimal getAmount() { return amount; }
  public String getCurrency() { return currency; }
  public String getAssetId() { return assetId; }
  public String getBranchId() { return branchId; }
  public String getCorrelationId() { return correlationId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getReversalOfEntryId() { return reversalOfEntryId; }
  public LocalDateTime getEffectiveAt() { return effectiveAt; }
  public LocalDateTime getPostedAt() { return postedAt; }
  public String getCreatedBy() { return createdBy; }
}
