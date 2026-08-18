package com.claritycam.platform.model.booking;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "checkout_holds")
public class CheckoutHoldReservation {
  @Id
  @Column(length = 64)
  private String token;

  @Column(nullable = false)
  private LocalDateTime pickupTime;

  @Column(nullable = false)
  private LocalDateTime returnTime;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "checkout_hold_items", joinColumns = @JoinColumn(name = "hold_token"))
  @MapKeyColumn(name = "product_id", length = 64)
  @Column(name = "quantity", nullable = false)
  private Map<String, Integer> items = new LinkedHashMap<>();

  @Column(length = 64)
  private String bundleId;

  @Column(length = 64)
  private String promotionCode;

  @Column(length = 24)
  private String rentalRate;

  @Column(nullable = false, length = 20)
  private String ownerPhone;

  @Column(length = 64)
  private String identityUploadToken;

  @Column(length = 64)
  private String paymentProofUploadToken;

  @Column(nullable = false)
  private Instant expiresAt;

  protected CheckoutHoldReservation() {}

  public CheckoutHoldReservation(String token, LocalDateTime pickupTime, LocalDateTime returnTime,
      Map<String, Integer> items, String bundleId, String promotionCode, String rentalRate,
      String ownerPhone, String identityUploadToken, String paymentProofUploadToken, Instant expiresAt) {
    this.token = token;
    this.pickupTime = pickupTime;
    this.returnTime = returnTime;
    this.items.putAll(items);
    this.bundleId = bundleId;
    this.promotionCode = promotionCode;
    this.rentalRate = rentalRate;
    this.ownerPhone = ownerPhone;
    this.identityUploadToken = identityUploadToken;
    this.paymentProofUploadToken = paymentProofUploadToken;
    this.expiresAt = expiresAt;
  }

  public String getToken() { return token; }
  public LocalDateTime getPickupTime() { return pickupTime; }
  public LocalDateTime getReturnTime() { return returnTime; }
  public Map<String, Integer> getItems() { return Map.copyOf(items); }
  public String getBundleId() { return bundleId; }
  public String getPromotionCode() { return promotionCode; }
  public String getRentalRate() { return rentalRate; }
  public String getOwnerPhone() { return ownerPhone; }
  public String getIdentityUploadToken() { return identityUploadToken; }
  public String getPaymentProofUploadToken() { return paymentProofUploadToken; }
  public Instant getExpiresAt() { return expiresAt; }

  public void attachPaymentProof(String uploadToken) {
    this.paymentProofUploadToken = uploadToken;
  }

  public boolean overlaps(LocalDateTime from, LocalDateTime to) {
    return pickupTime.isBefore(to) && returnTime.isAfter(from);
  }
}
