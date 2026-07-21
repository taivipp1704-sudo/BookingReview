package com.claritycam.platform.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "identity_uploads")
public class IdentityUpload {
  @Id
  private String id;
  private String ownerPhone;
  private String frontStorageKey;
  private String backStorageKey;
  private LocalDateTime createdAt;
  private LocalDateTime expiresAt;
  private LocalDateTime consumedAt;

  protected IdentityUpload() {}

  public IdentityUpload(String id, String ownerPhone, String frontStorageKey, String backStorageKey,
      LocalDateTime createdAt, LocalDateTime expiresAt) {
    this.id = id;
    this.ownerPhone = ownerPhone;
    this.frontStorageKey = frontStorageKey;
    this.backStorageKey = backStorageKey;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public boolean isUsableBy(String phone, LocalDateTime now) {
    return ownerPhone.equals(phone) && consumedAt == null && expiresAt.isAfter(now);
  }

  public void consume(LocalDateTime now) { this.consumedAt = now; }

  public String getId() { return id; }
  public String getOwnerPhone() { return ownerPhone; }
  public String getFrontStorageKey() { return frontStorageKey; }
  public String getBackStorageKey() { return backStorageKey; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public LocalDateTime getConsumedAt() { return consumedAt; }
}
