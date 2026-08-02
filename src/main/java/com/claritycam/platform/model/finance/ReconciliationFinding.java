package com.claritycam.platform.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_reconciliation_findings")
public class ReconciliationFinding {
  @Id private String id;
  private String bookingId;
  private String code;
  private String severity;
  private String state;
  @Column(length = 1000) private String detail;
  private LocalDateTime detectedAt;

  protected ReconciliationFinding() {}
  public ReconciliationFinding(String bookingId, String code, String severity, String detail) {
    this.id = "FND-" + CommercialSnapshotLine.compactId();
    this.bookingId = bookingId;
    this.code = code;
    this.severity = severity;
    this.state = "OPEN";
    this.detail = detail;
    this.detectedAt = LocalDateTime.now();
  }
  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getCode() { return code; }
  public String getSeverity() { return severity; }
  public String getState() { return state; }
  public String getDetail() { return detail; }
  public LocalDateTime getDetectedAt() { return detectedAt; }
}
