package com.claritycam.platform.model.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "commercial_snapshots")
public class CommercialSnapshot {
  @Id private String bookingId;
  private String pricingRuleVersion;
  private String currency;
  private String branchId;
  private BigDecimal subtotalAmount;
  private BigDecimal discountAmount;
  private BigDecimal netRentalAmount;
  private BigDecimal equipmentDeposit;
  private BigDecimal reservationDeposit;
  private LocalDateTime pickupTime;
  private LocalDateTime returnTime;
  private LocalDateTime frozenAt;
  private String frozenBy;

  protected CommercialSnapshot() {}

  public CommercialSnapshot(String bookingId, BigDecimal subtotalAmount, BigDecimal discountAmount,
      BigDecimal netRentalAmount, BigDecimal equipmentDeposit, BigDecimal reservationDeposit,
      LocalDateTime pickupTime, LocalDateTime returnTime, String actor) {
    this.bookingId = bookingId;
    this.pricingRuleVersion = "RENTAL_V1";
    this.currency = "VND";
    this.branchId = "MAIN";
    this.subtotalAmount = amount(subtotalAmount);
    this.discountAmount = amount(discountAmount);
    this.netRentalAmount = amount(netRentalAmount);
    this.equipmentDeposit = amount(equipmentDeposit);
    this.reservationDeposit = amount(reservationDeposit);
    this.pickupTime = pickupTime;
    this.returnTime = returnTime;
    this.frozenAt = LocalDateTime.now();
    this.frozenBy = actor;
  }

  public String getBookingId() { return bookingId; }
  public String getPricingRuleVersion() { return pricingRuleVersion; }
  public String getCurrency() { return currency; }
  public String getBranchId() { return branchId; }
  public BigDecimal getSubtotalAmount() { return amount(subtotalAmount); }
  public BigDecimal getDiscountAmount() { return amount(discountAmount); }
  public BigDecimal getNetRentalAmount() { return amount(netRentalAmount); }
  public BigDecimal getEquipmentDeposit() { return amount(equipmentDeposit); }
  public BigDecimal getReservationDeposit() { return amount(reservationDeposit); }
  public LocalDateTime getPickupTime() { return pickupTime; }
  public LocalDateTime getReturnTime() { return returnTime; }
  public LocalDateTime getFrozenAt() { return frozenAt; }
  public String getFrozenBy() { return frozenBy; }

  static BigDecimal amount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO); }
}
