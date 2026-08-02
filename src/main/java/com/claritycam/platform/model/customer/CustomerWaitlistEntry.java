package com.claritycam.platform.model.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_waitlist", uniqueConstraints = {
    @UniqueConstraint(name = "uk_customer_waitlist_phone", columnNames = "phone_normalized"),
    @UniqueConstraint(name = "uk_customer_waitlist_account", columnNames = "account_id")
})
public class CustomerWaitlistEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private String accountId;

  @Column(name = "phone_normalized", nullable = false, length = 20)
  private String phoneNormalized;

  @Column(nullable = false, length = 180)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private WaitlistStatus status;

  @Column(nullable = false, length = 40)
  private String source;

  @Column(nullable = false, length = 32)
  private String consentVersion;

  @Column(nullable = false)
  private LocalDateTime consentedAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column(length = 1000)
  private String adminNote;

  protected CustomerWaitlistEntry() {}

  public CustomerWaitlistEntry(String accountId, String phoneNormalized, String name,
      String source, String consentVersion) {
    this.accountId = accountId;
    this.phoneNormalized = phoneNormalized;
    this.name = name;
    this.status = WaitlistStatus.WAITLISTED;
    this.source = source;
    this.consentVersion = consentVersion;
    this.consentedAt = LocalDateTime.now();
    this.createdAt = this.consentedAt;
    this.updatedAt = this.consentedAt;
  }

  public String slotCode() {
    return id == null ? null : "AMY-%06d".formatted(id);
  }

  public Long getId() { return id; }
  public String getAccountId() { return accountId; }
  public String getPhoneNormalized() { return phoneNormalized; }
  public String getName() { return name; }
  public WaitlistStatus getStatus() { return status; }
  public String getSource() { return source; }
  public String getConsentVersion() { return consentVersion; }
  public LocalDateTime getConsentedAt() { return consentedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public String getAdminNote() { return adminNote; }
}
