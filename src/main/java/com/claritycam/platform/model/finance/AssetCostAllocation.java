package com.claritycam.platform.model.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_cost_allocations")
public class AssetCostAllocation {
  @Id private String id;
  private String expenseId;
  private String bookingId;
  private String assetId;
  private BigDecimal amount;
  private String ruleVersion;
  private LocalDateTime allocatedAt;

  protected AssetCostAllocation() {}

  public AssetCostAllocation(String expenseId, String bookingId, String assetId, BigDecimal amount) {
    this.id = "COST-" + CommercialSnapshotLine.compactId();
    this.expenseId = expenseId;
    this.bookingId = bookingId;
    this.assetId = assetId;
    this.amount = CommercialSnapshot.amount(amount);
    this.ruleVersion = "DIRECT_SOURCE_V1";
    this.allocatedAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getExpenseId() { return expenseId; }
  public String getBookingId() { return bookingId; }
  public String getAssetId() { return assetId; }
  public BigDecimal getAmount() { return CommercialSnapshot.amount(amount); }
  public String getRuleVersion() { return ruleVersion; }
  public LocalDateTime getAllocatedAt() { return allocatedAt; }
}
