package com.claritycam.platform.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_revenue_allocations")
public class AssetRevenueAllocation {
  @Id private String id;
  private String bookingId;
  private String productId;
  private String assetId;
  private BigDecimal numerator;
  private BigDecimal denominator;
  private BigDecimal allocationRate;
  private BigDecimal amount;
  private String ruleVersion;
  private LocalDateTime allocatedAt;

  protected AssetRevenueAllocation() {}

  public AssetRevenueAllocation(String bookingId, String productId, String assetId, BigDecimal numerator,
      BigDecimal denominator, BigDecimal allocationRate, BigDecimal amount) {
    this.id = "ARA-" + CommercialSnapshotLine.compactId();
    this.bookingId = bookingId;
    this.productId = productId;
    this.assetId = assetId;
    this.numerator = numerator;
    this.denominator = denominator;
    this.allocationRate = allocationRate;
    this.amount = amount;
    this.ruleVersion = "LIST_PRICE_V1";
    this.allocatedAt = LocalDateTime.now();
  }
  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getProductId() { return productId; }
  public String getAssetId() { return assetId; }
  public BigDecimal getNumerator() { return numerator; }
  public BigDecimal getDenominator() { return denominator; }
  public BigDecimal getAllocationRate() { return allocationRate; }
  public BigDecimal getAmount() { return amount; }
  public String getRuleVersion() { return ruleVersion; }
  public LocalDateTime getAllocatedAt() { return allocatedAt; }
}
