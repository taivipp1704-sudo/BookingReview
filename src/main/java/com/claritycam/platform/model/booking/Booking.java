package com.claritycam.platform.model.booking;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
public class Booking {
  @Id
  private String id;
  private String customerName;
  private String phone;
  private String phoneNormalized;
  private String trustScore;

  @Enumerated(EnumType.STRING)
  private BookingState state;

  private BigDecimal totalAmount;
  private BigDecimal subtotalAmount;
  private BigDecimal discountAmount;
  private BigDecimal depositRequired;
  private BigDecimal depositPaid;
  private BigDecimal equipmentDeposit;
  private BigDecimal bookingDeposit;
  private BigDecimal amountDueNow;
  private LocalDateTime pickupTime;
  private LocalDateTime returnTime;
  private boolean earlyPickupRequested;
  private LocalDateTime earlyPickupTime;
  private boolean earlyPickupApproved;
  private BigDecimal earlyPickupFee;
  private boolean lateReturnRequested;
  private LocalDateTime lateReturnTime;
  private boolean lateReturnApproved;
  @Column(precision = 19, scale = 2)
  private BigDecimal lateReturnFee;
  private boolean kycApproved;
  private String identityFrontReference;
  private String identityBackReference;
  private String paymentProofReference;
  private String bankAccountReference;
  private String bundleId;
  private String promotionCode;
  private String storeBranchId;
  private String storeBranchCode;
  private String storeBranchName;
  private String storeBranchAddress;
  private String note;
  private String lastActionReason;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime holdExpiresAt;
  @Version
  private long version;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "booking_id")
  private List<BookingLine> items = new ArrayList<>();

  protected Booking() {}

  public Booking(
      String id,
      String customerName,
      String phone,
      String phoneNormalized,
      BookingState state,
      BigDecimal totalAmount,
      BigDecimal depositRequired,
      LocalDateTime pickupTime,
      LocalDateTime returnTime,
      String bundleId,
      String note,
      List<BookingLine> items) {
    this.id = id;
    this.customerName = customerName;
    this.phone = phone;
    this.phoneNormalized = phoneNormalized;
    this.trustScore = "VERIFIED_PHONE";
    this.state = state;
    this.totalAmount = totalAmount;
    this.subtotalAmount = totalAmount;
    this.discountAmount = BigDecimal.ZERO;
    this.depositRequired = depositRequired;
    this.depositPaid = BigDecimal.ZERO;
    this.equipmentDeposit = depositRequired;
    this.bookingDeposit = BigDecimal.ZERO;
    this.amountDueNow = BigDecimal.ZERO;
    this.pickupTime = pickupTime;
    this.returnTime = returnTime;
    this.earlyPickupRequested = false;
    this.earlyPickupApproved = false;
    this.earlyPickupFee = BigDecimal.ZERO;
    this.lateReturnRequested = false;
    this.lateReturnApproved = false;
    this.lateReturnFee = BigDecimal.ZERO;
    this.kycApproved = false;
    this.bundleId = bundleId;
    this.note = note == null ? "" : note.trim();
    this.createdAt = LocalDateTime.now();
    this.updatedAt = this.createdAt;
    this.holdExpiresAt = this.createdAt.plusHours(1);
    this.items.addAll(items);
  }

  public void changeState(BookingState nextState, String reason) {
    this.state = nextState;
    this.lastActionReason = reason == null ? "" : reason.trim();
    this.updatedAt = LocalDateTime.now();
    if (nextState == BookingState.NEGOTIATION || nextState == BookingState.CONDITIONAL
        || nextState == BookingState.TEMP_HOLD) this.holdExpiresAt = this.updatedAt.plusHours(24);
    if (nextState == BookingState.CONFIRMED || nextState == BookingState.READY_FOR_PICKUP
        || nextState == BookingState.IN_USE || nextState == BookingState.COMPLETED
        || nextState == BookingState.REJECTED) this.holdExpiresAt = null;
  }

  public void requestEarlyPickup(LocalDateTime requestedTime) {
    if (requestedTime != null && !requestedTime.isBefore(pickupTime)) {
      throw new IllegalArgumentException("Thời gian nhận sớm phải trước thời gian nhận máy chính thức.");
    }
    this.earlyPickupRequested = requestedTime != null;
    this.earlyPickupTime = requestedTime;
  }

  public void reviewEarlyPickup(boolean approved, BigDecimal fee, String reason) {
    BigDecimal previousFee = this.earlyPickupFee == null ? BigDecimal.ZERO : this.earlyPickupFee;
    this.totalAmount = this.totalAmount.subtract(previousFee);
    this.earlyPickupApproved = approved;
    this.earlyPickupFee = approved && fee != null ? fee.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    this.totalAmount = this.totalAmount.add(this.earlyPickupFee);
    this.amountDueNow = getBookingDeposit();
    this.lastActionReason = reason == null ? "" : reason.trim();
    this.updatedAt = LocalDateTime.now();
  }

  public void requestLateReturn(LocalDateTime requestedTime) {
    if (requestedTime != null && !requestedTime.isAfter(returnTime)) {
      throw new IllegalArgumentException("Thời gian trả trễ phải sau thời gian trả máy đã chọn.");
    }
    this.lateReturnRequested = requestedTime != null;
    this.lateReturnTime = requestedTime;
  }

  public void reviewLateReturn(boolean approved, BigDecimal fee, String reason) {
    BigDecimal previousFee = this.lateReturnFee == null ? BigDecimal.ZERO : this.lateReturnFee;
    this.totalAmount = this.totalAmount.subtract(previousFee);
    this.lateReturnApproved = approved;
    this.lateReturnFee = approved && fee != null ? fee.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    this.totalAmount = this.totalAmount.add(this.lateReturnFee);
    this.amountDueNow = getBookingDeposit();
    this.lastActionReason = reason == null ? "" : reason.trim();
    this.updatedAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getCustomerName() { return customerName; }
  public String getPhone() { return phone; }
  public String getPhoneNormalized() { return phoneNormalized; }
  public String getTrustScore() { return trustScore; }
  public BookingState getState() { return state; }
  public BigDecimal getTotalAmount() { return totalAmount; }
  public BigDecimal getDepositRequired() { return depositRequired; }
  public BigDecimal getDepositPaid() { return depositPaid; }
  public BigDecimal getEquipmentDeposit() { return equipmentDeposit == null ? getDepositRequired() : equipmentDeposit; }
  public BigDecimal getBookingDeposit() { return bookingDeposit == null ? BigDecimal.ZERO : bookingDeposit; }
  public BigDecimal getAmountDueNow() { return amountDueNow == null ? getBookingDeposit() : amountDueNow; }
  public BigDecimal getAmountDueBeforeHandover() { return getTotalAmount().add(getDepositRequired()); }
  public void applyPaymentBreakdown(BigDecimal equipmentDeposit, BigDecimal bookingDeposit, BigDecimal amountDueNow) {
    this.equipmentDeposit = equipmentDeposit == null ? BigDecimal.ZERO : equipmentDeposit.max(BigDecimal.ZERO);
    this.bookingDeposit = bookingDeposit == null ? BigDecimal.ZERO : bookingDeposit.max(BigDecimal.ZERO);
    this.depositRequired = this.equipmentDeposit.add(this.bookingDeposit);
    this.amountDueNow = amountDueNow == null ? this.bookingDeposit : amountDueNow.max(BigDecimal.ZERO);
  }
  public LocalDateTime getPickupTime() { return pickupTime; }
  public LocalDateTime getReturnTime() { return returnTime; }
  public boolean isEarlyPickupRequested() { return earlyPickupRequested; }
  public LocalDateTime getEarlyPickupTime() { return earlyPickupTime; }
  public boolean isEarlyPickupApproved() { return earlyPickupApproved; }
  public BigDecimal getEarlyPickupFee() { return earlyPickupFee == null ? BigDecimal.ZERO : earlyPickupFee; }
  public boolean isLateReturnRequested() { return lateReturnRequested; }
  public LocalDateTime getLateReturnTime() { return lateReturnTime; }
  public boolean isLateReturnApproved() { return lateReturnApproved; }
  public BigDecimal getLateReturnFee() { return lateReturnFee == null ? BigDecimal.ZERO : lateReturnFee; }
  public boolean isKycApproved() { return kycApproved; }
  public String getIdentityFrontReference() { return identityFrontReference; }
  public String getIdentityBackReference() { return identityBackReference; }
  public void attachIdentityDocuments(String front, String back) { this.identityFrontReference = front; this.identityBackReference = back; }
  public String getPaymentProofReference() { return paymentProofReference; }
  public void attachPaymentProof(String reference) { this.paymentProofReference = reference; }
  public String getBankAccountReference() { return bankAccountReference; }
  public void attachBankAccount(String reference) { this.bankAccountReference = reference; }
  public void applyPromotion(BigDecimal subtotal, BigDecimal discount, String code) {
    this.subtotalAmount = subtotal;
    this.discountAmount = discount == null ? BigDecimal.ZERO : discount;
    this.promotionCode = code;
  }
  public String getBundleId() { return bundleId; }
  public String getPromotionCode() { return promotionCode; }
  public String getStoreBranchId() { return storeBranchId; }
  public String getStoreBranchCode() { return storeBranchCode; }
  public String getStoreBranchName() { return storeBranchName; }
  public String getStoreBranchAddress() { return storeBranchAddress; }
  public void assignStoreBranch(String id, String code, String name, String address) {
    this.storeBranchId = id;
    this.storeBranchCode = code;
    this.storeBranchName = name;
    this.storeBranchAddress = address;
  }
  public BigDecimal getSubtotalAmount() { return subtotalAmount == null ? totalAmount : subtotalAmount; }
  public BigDecimal getDiscountAmount() { return discountAmount == null ? BigDecimal.ZERO : discountAmount; }
  public String getNote() { return note; }
  public String getLastActionReason() { return lastActionReason; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
  public long getVersion() { return version; }
  public boolean hasActiveHold(LocalDateTime now) { return holdExpiresAt == null || holdExpiresAt.isAfter(now); }
  public List<BookingLine> getItems() { return items; }
}
