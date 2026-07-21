package com.claritycam.platform.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
  @Id
  private String id;
  private String levelCode;
  private String name;
  private String brand;
  private String category;
  private BigDecimal hourlyPrice;
  private BigDecimal halfDayPrice;
  private BigDecimal dailyPrice;
  private BigDecimal twoDayPrice;
  private BigDecimal multiDayPrice;
  private int multiDayDays;
  private BigDecimal extraDayPrice;
  private BigDecimal equipmentDeposit;
  private BigDecimal bookingDeposit;
  private BigDecimal lateFeePerHour;
  private BigDecimal identityViolationFee;
  private BigDecimal unauthorizedTransferFee;
  private BigDecimal impactPenaltyPercent;
  private BigDecimal damageLiabilityLimit;
  private boolean included;
  private boolean active;
  private String imageUrl;
  private String specs;
  private String trackingMode;
  private String serialPrefix;
  private long bookingCountBase;

  @Transient
  private long bookingCount;

  @Column(length = 4000)
  private String customAttributes;

  protected Product() {}

  public Product(String id, String levelCode, String name, String brand, String category, BigDecimal dailyPrice,
      boolean included, boolean active, String imageUrl, String specs, String trackingMode, String serialPrefix) {
    this.id = id;
    this.levelCode = levelCode;
    this.name = name;
    this.brand = brand;
    this.category = category;
    this.hourlyPrice = BigDecimal.ZERO;
    this.halfDayPrice = BigDecimal.ZERO;
    this.dailyPrice = dailyPrice;
    this.twoDayPrice = BigDecimal.ZERO;
    this.multiDayPrice = BigDecimal.ZERO;
    this.multiDayDays = 3;
    this.extraDayPrice = BigDecimal.ZERO;
    this.equipmentDeposit = BigDecimal.ZERO;
    this.bookingDeposit = BigDecimal.ZERO;
    this.lateFeePerHour = BigDecimal.ZERO;
    this.identityViolationFee = BigDecimal.ZERO;
    this.unauthorizedTransferFee = BigDecimal.ZERO;
    this.impactPenaltyPercent = BigDecimal.ZERO;
    this.damageLiabilityLimit = BigDecimal.ZERO;
    this.included = included;
    this.active = active;
    this.imageUrl = imageUrl;
    this.specs = specs;
    this.trackingMode = trackingMode;
    this.serialPrefix = serialPrefix;
    this.customAttributes = "{}";
  }

  public Product(String id, ProductPayload payload) {
    this.id = id;
    this.hourlyPrice = BigDecimal.ZERO;
    this.halfDayPrice = BigDecimal.ZERO;
    this.twoDayPrice = BigDecimal.ZERO;
    this.multiDayPrice = BigDecimal.ZERO;
    this.multiDayDays = 3;
    this.extraDayPrice = BigDecimal.ZERO;
    this.equipmentDeposit = BigDecimal.ZERO;
    this.bookingDeposit = BigDecimal.ZERO;
    this.lateFeePerHour = BigDecimal.ZERO;
    this.identityViolationFee = BigDecimal.ZERO;
    this.unauthorizedTransferFee = BigDecimal.ZERO;
    this.impactPenaltyPercent = BigDecimal.ZERO;
    this.damageLiabilityLimit = BigDecimal.ZERO;
    apply(payload);
  }

  public void apply(ProductPayload payload) {
    this.levelCode = payload.levelCode().trim();
    this.name = payload.name().trim();
    this.brand = payload.brand().trim();
    this.category = payload.category().trim();
    if (payload.hourlyPrice() != null) this.hourlyPrice = payload.hourlyPrice();
    if (payload.halfDayPrice() != null) this.halfDayPrice = payload.halfDayPrice();
    this.dailyPrice = payload.dailyPrice();
    if (payload.twoDayPrice() != null) this.twoDayPrice = payload.twoDayPrice();
    if (payload.multiDayPrice() != null) this.multiDayPrice = payload.multiDayPrice();
    if (payload.multiDayDays() != null) this.multiDayDays = payload.multiDayDays();
    if (payload.extraDayPrice() != null) this.extraDayPrice = payload.extraDayPrice();
    if (payload.equipmentDeposit() != null) this.equipmentDeposit = payload.equipmentDeposit();
    if (payload.bookingDeposit() != null) this.bookingDeposit = payload.bookingDeposit();
    if (payload.lateFeePerHour() != null) this.lateFeePerHour = payload.lateFeePerHour();
    if (payload.identityViolationFee() != null) this.identityViolationFee = payload.identityViolationFee();
    if (payload.unauthorizedTransferFee() != null) this.unauthorizedTransferFee = payload.unauthorizedTransferFee();
    if (payload.impactPenaltyPercent() != null) this.impactPenaltyPercent = payload.impactPenaltyPercent();
    if (payload.damageLiabilityLimit() != null) this.damageLiabilityLimit = payload.damageLiabilityLimit();
    this.included = payload.included();
    this.active = payload.active();
    this.imageUrl = payload.imageUrl() == null ? "" : payload.imageUrl().trim();
    this.specs = payload.specs().trim();
    this.trackingMode = payload.trackingMode();
    this.serialPrefix = payload.serialPrefix() == null ? "" : payload.serialPrefix().trim();
    this.bookingCountBase = payload.bookingCountBase();
    this.customAttributes = payload.customAttributes() == null || payload.customAttributes().isBlank()
        ? "{}" : payload.customAttributes().trim();
  }

  public void updateCustomAttributes(String customAttributes) {
    this.customAttributes = customAttributes == null || customAttributes.isBlank() ? "{}" : customAttributes;
  }

  public void updateBookingCountBase(long bookingCountBase) {
    this.bookingCountBase = Math.max(0, bookingCountBase);
  }

  public void updateBookingCount(long actualBookingCount) {
    this.bookingCount = bookingCountBase + Math.max(0, actualBookingCount);
  }

  public void configurePricing(BigDecimal hourlyPrice, BigDecimal multiDayPrice, int multiDayDays) {
    this.hourlyPrice = hourlyPrice == null ? BigDecimal.ZERO : hourlyPrice.max(BigDecimal.ZERO);
    this.multiDayPrice = multiDayPrice == null ? BigDecimal.ZERO : multiDayPrice.max(BigDecimal.ZERO);
    this.multiDayDays = Math.max(2, multiDayDays);
  }

  public void configureCommercialTerms(BigDecimal halfDayPrice, BigDecimal twoDayPrice,
      BigDecimal extraDayPrice, BigDecimal equipmentDeposit, BigDecimal bookingDeposit,
      BigDecimal lateFeePerHour, BigDecimal identityViolationFee, BigDecimal unauthorizedTransferFee,
      BigDecimal impactPenaltyPercent, BigDecimal damageLiabilityLimit) {
    this.halfDayPrice = nonNegative(halfDayPrice);
    this.twoDayPrice = nonNegative(twoDayPrice);
    this.extraDayPrice = nonNegative(extraDayPrice);
    this.equipmentDeposit = nonNegative(equipmentDeposit);
    this.bookingDeposit = nonNegative(bookingDeposit);
    this.lateFeePerHour = nonNegative(lateFeePerHour);
    this.identityViolationFee = nonNegative(identityViolationFee);
    this.unauthorizedTransferFee = nonNegative(unauthorizedTransferFee);
    this.impactPenaltyPercent = nonNegative(impactPenaltyPercent).min(BigDecimal.valueOf(100));
    this.damageLiabilityLimit = nonNegative(damageLiabilityLimit);
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
  }

  public void deactivate() { this.active = false; }

  public void migrateTrackingMode(String trackingMode) {
    this.trackingMode = trackingMode;
  }

  public String getId() { return id; }
  public String getLevelCode() { return levelCode; }
  public String getName() { return name; }
  public String getBrand() { return brand; }
  public String getCategory() { return category; }
  public BigDecimal getHourlyPrice() { return hourlyPrice == null ? BigDecimal.ZERO : hourlyPrice; }
  public BigDecimal getHalfDayPrice() { return halfDayPrice == null ? BigDecimal.ZERO : halfDayPrice; }
  public BigDecimal getDailyPrice() { return dailyPrice; }
  public BigDecimal getTwoDayPrice() { return twoDayPrice == null ? BigDecimal.ZERO : twoDayPrice; }
  public BigDecimal getMultiDayPrice() { return multiDayPrice == null ? BigDecimal.ZERO : multiDayPrice; }
  public int getMultiDayDays() { return multiDayDays < 2 ? 3 : multiDayDays; }
  public BigDecimal getExtraDayPrice() { return extraDayPrice == null ? BigDecimal.ZERO : extraDayPrice; }
  public BigDecimal getEquipmentDeposit() { return equipmentDeposit == null ? BigDecimal.ZERO : equipmentDeposit; }
  public BigDecimal getBookingDeposit() { return bookingDeposit == null ? BigDecimal.ZERO : bookingDeposit; }
  public BigDecimal getLateFeePerHour() { return lateFeePerHour == null ? BigDecimal.ZERO : lateFeePerHour; }
  public BigDecimal getIdentityViolationFee() { return identityViolationFee == null ? BigDecimal.ZERO : identityViolationFee; }
  public BigDecimal getUnauthorizedTransferFee() { return unauthorizedTransferFee == null ? BigDecimal.ZERO : unauthorizedTransferFee; }
  public BigDecimal getImpactPenaltyPercent() { return impactPenaltyPercent == null ? BigDecimal.ZERO : impactPenaltyPercent; }
  public BigDecimal getDamageLiabilityLimit() { return damageLiabilityLimit == null ? BigDecimal.ZERO : damageLiabilityLimit; }
  public boolean isIncluded() { return included; }
  public boolean isActive() { return active; }
  public String getImageUrl() { return imageUrl; }
  public String getSpecs() { return specs; }
  public String getTrackingMode() { return trackingMode; }
  public String getSerialPrefix() { return serialPrefix; }
  public String getCustomAttributes() { return customAttributes; }
  public long getBookingCountBase() { return bookingCountBase; }
  public long getBookingCount() { return bookingCount; }
}
