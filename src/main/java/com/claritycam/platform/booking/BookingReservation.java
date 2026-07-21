package com.claritycam.platform.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_reservations")
public class BookingReservation {
  @Id
  private String id;
  private String bookingId;
  private String productId;
  private int quantity;

  @Enumerated(EnumType.STRING)
  private ReservationType type;

  @Enumerated(EnumType.STRING)
  private ReservationState state;

  private LocalDateTime pickupTime;
  private LocalDateTime returnTime;
  private LocalDateTime expiresAt;
  private LocalDateTime createdAt;
  private LocalDateTime releasedAt;
  private String createdBy;

  protected BookingReservation() {}

  public BookingReservation(String bookingId, String productId, int quantity, ReservationType type,
      LocalDateTime pickupTime, LocalDateTime returnTime, LocalDateTime expiresAt, String createdBy) {
    this.id = "RSV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    this.bookingId = bookingId;
    this.productId = productId;
    this.quantity = quantity;
    this.type = type;
    this.state = ReservationState.ACTIVE;
    this.pickupTime = pickupTime;
    this.returnTime = returnTime;
    this.expiresAt = expiresAt;
    this.createdAt = LocalDateTime.now();
    this.createdBy = createdBy;
  }

  public void release(ReservationState nextState) {
    if (state != ReservationState.ACTIVE) return;
    this.state = nextState;
    this.releasedAt = LocalDateTime.now();
  }

  public boolean overlaps(LocalDateTime from, LocalDateTime to) {
    return pickupTime.isBefore(to) && returnTime.isAfter(from);
  }

  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getProductId() { return productId; }
  public int getQuantity() { return quantity; }
  public ReservationType getType() { return type; }
  public ReservationState getState() { return state; }
  public LocalDateTime getPickupTime() { return pickupTime; }
  public LocalDateTime getReturnTime() { return returnTime; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getReleasedAt() { return releasedAt; }
  public String getCreatedBy() { return createdBy; }
}
