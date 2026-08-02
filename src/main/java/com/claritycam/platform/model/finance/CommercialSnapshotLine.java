package com.claritycam.platform.model.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "commercial_snapshot_lines")
public class CommercialSnapshotLine {
  @Id private String id;
  private String bookingId;
  private String productId;
  private int quantity;
  private BigDecimal listedUnitPrice;
  private BigDecimal chargeUnitPrice;
  private BigDecimal chargeAmount;
  private String pricingMode;
  private int billableUnits;
  private boolean voucherEligible;
  private String pricingRuleVersion;

  protected CommercialSnapshotLine() {}

  public CommercialSnapshotLine(String bookingId, String productId, int quantity, BigDecimal listedUnitPrice,
      BigDecimal chargeUnitPrice, BigDecimal chargeAmount, String pricingMode, int billableUnits,
      String pricingRuleVersion) {
    this.id = "CSL-" + compactId();
    this.bookingId = bookingId;
    this.productId = productId;
    this.quantity = quantity;
    this.listedUnitPrice = CommercialSnapshot.amount(listedUnitPrice);
    this.chargeUnitPrice = CommercialSnapshot.amount(chargeUnitPrice);
    this.chargeAmount = CommercialSnapshot.amount(chargeAmount);
    this.pricingMode = pricingMode;
    this.billableUnits = billableUnits;
    this.voucherEligible = true;
    this.pricingRuleVersion = pricingRuleVersion;
  }

  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getProductId() { return productId; }
  public int getQuantity() { return quantity; }
  public BigDecimal getListedUnitPrice() { return CommercialSnapshot.amount(listedUnitPrice); }
  public BigDecimal getChargeUnitPrice() { return CommercialSnapshot.amount(chargeUnitPrice); }
  public BigDecimal getChargeAmount() { return CommercialSnapshot.amount(chargeAmount); }
  public String getPricingMode() { return pricingMode; }
  public int getBillableUnits() { return billableUnits; }
  public boolean isVoucherEligible() { return voucherEligible; }
  public String getPricingRuleVersion() { return pricingRuleVersion; }

  static String compactId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(); }
}
