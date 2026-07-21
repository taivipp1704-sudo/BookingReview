package com.claritycam.platform.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "booking_lines")
public class BookingLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String productId;
  private String serialId;
  private int quantity;
  private BigDecimal listedUnitPriceSnapshot;
  private BigDecimal chargeUnitPriceSnapshot;
  private BigDecimal chargeAmountSnapshot;
  private String pricingModeSnapshot;
  private int billableUnitsSnapshot;
  private String pricingRuleVersion;

  protected BookingLine() {}

  public BookingLine(String productId, String serialId, int quantity) {
    this(productId, serialId, quantity, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        "LEGACY", 0, "RENTAL_V1");
  }

  public BookingLine(String productId, String serialId, int quantity, BigDecimal listedUnitPriceSnapshot,
      BigDecimal chargeUnitPriceSnapshot, BigDecimal chargeAmountSnapshot, String pricingModeSnapshot,
      int billableUnitsSnapshot, String pricingRuleVersion) {
    this.productId = productId;
    this.serialId = serialId;
    this.quantity = quantity;
    this.listedUnitPriceSnapshot = nonNegative(listedUnitPriceSnapshot);
    this.chargeUnitPriceSnapshot = nonNegative(chargeUnitPriceSnapshot);
    this.chargeAmountSnapshot = nonNegative(chargeAmountSnapshot);
    this.pricingModeSnapshot = pricingModeSnapshot == null ? "LEGACY" : pricingModeSnapshot;
    this.billableUnitsSnapshot = Math.max(0, billableUnitsSnapshot);
    this.pricingRuleVersion = pricingRuleVersion == null ? "RENTAL_V1" : pricingRuleVersion;
  }

  public Long getId() { return id; }
  public String getProductId() { return productId; }
  public String getSerialId() { return serialId; }
  public int getQuantity() { return quantity; }
  public BigDecimal getListedUnitPriceSnapshot() { return nonNegative(listedUnitPriceSnapshot); }
  public BigDecimal getChargeUnitPriceSnapshot() { return nonNegative(chargeUnitPriceSnapshot); }
  public BigDecimal getChargeAmountSnapshot() { return nonNegative(chargeAmountSnapshot); }
  public String getPricingModeSnapshot() { return pricingModeSnapshot == null ? "LEGACY" : pricingModeSnapshot; }
  public int getBillableUnitsSnapshot() { return billableUnitsSnapshot; }
  public String getPricingRuleVersion() { return pricingRuleVersion == null ? "RENTAL_V1" : pricingRuleVersion; }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
  }
}
