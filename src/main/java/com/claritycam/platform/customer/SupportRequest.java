package com.claritycam.platform.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_support_requests")
public class SupportRequest {
  @Id private String id;
  private String phoneNormalized;
  private String bookingId;
  private String type;
  private String message;
  private String status;
  private String adminNote;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  protected SupportRequest() {}
  public SupportRequest(String id, String phoneNormalized, String bookingId, String type, String message) {
    this.id=id; this.phoneNormalized=phoneNormalized; this.bookingId=bookingId; this.type=type; this.message=message;
    this.status="OPEN"; this.adminNote=""; this.createdAt=LocalDateTime.now(); this.updatedAt=this.createdAt;
  }
  public void review(String status, String note) { this.status=status; this.adminNote=note == null ? "" : note.trim(); this.updatedAt=LocalDateTime.now(); }
  public String getId(){return id;} public String getPhoneNormalized(){return phoneNormalized;} public String getBookingId(){return bookingId;}
  public String getType(){return type;} public String getMessage(){return message;} public String getStatus(){return status;}
  public String getAdminNote(){return adminNote;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
