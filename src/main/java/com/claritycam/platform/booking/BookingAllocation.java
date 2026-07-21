package com.claritycam.platform.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_allocations")
public class BookingAllocation {
  @Id
  private String id;
  private String bookingId;
  private String productId;
  private String serialId;
  private int quantity;

  @Enumerated(EnumType.STRING)
  private AllocationRole role;

  @Enumerated(EnumType.STRING)
  private AllocationState state;

  private String createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  protected BookingAllocation() {}

  public BookingAllocation(String bookingId, String productId, String serialId, int quantity,
      AllocationRole role, String createdBy) {
    this.id = "ALC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    this.bookingId = bookingId;
    this.productId = productId;
    this.serialId = serialId;
    this.quantity = quantity;
    this.role = role;
    this.state = AllocationState.ALLOCATED;
    this.createdBy = createdBy;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = this.createdAt;
  }

  public void changeState(AllocationState nextState) {
    this.state = nextState;
    this.updatedAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getBookingId() { return bookingId; }
  public String getProductId() { return productId; }
  public String getSerialId() { return serialId; }
  public int getQuantity() { return quantity; }
  public AllocationRole getRole() { return role; }
  public AllocationState getState() { return state; }
  public String getCreatedBy() { return createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
