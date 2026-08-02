package com.claritycam.platform.model.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_accounts")
public class CustomerAccount {
  @Id private String id;
  @Column(unique = true, nullable = false) private String phoneNormalized;
  @Column(nullable = false) private String name;
  @Column(unique = true, length = 255) private String email;
  @Column(length = 100) private String passwordHash;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private boolean mustChangePassword;
  private LocalDateTime createdAt;
  private LocalDateTime lastLoginAt;
  private int onboardingVersion;
  private LocalDateTime onboardingCompletedAt;

  protected CustomerAccount() {}

  public CustomerAccount(String id, String phoneNormalized, String name) {
    this.id = id;
    this.phoneNormalized = phoneNormalized;
    this.name = name;
    this.createdAt = LocalDateTime.now();
    this.lastLoginAt = null;
  }

  public void login(String nextName) {
    if (nextName != null && !nextName.isBlank()) this.name = nextName.trim();
    this.lastLoginAt = LocalDateTime.now();
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void updateProfile(String name, String email) {
    if (name != null && !name.isBlank()) this.name = name.trim();
    this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void resetPassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = true;
  }

  public void changePassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = false;
  }

  public void resetOnboarding() {
    this.onboardingVersion = 0;
    this.onboardingCompletedAt = null;
  }

  public void completeOnboarding(int version) {
    this.onboardingVersion = Math.max(this.onboardingVersion, version);
    this.onboardingCompletedAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getPhoneNormalized() { return phoneNormalized; }
  public String getName() { return name; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public boolean isActive() { return active; }
  public boolean isMustChangePassword() { return mustChangePassword; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getLastLoginAt() { return lastLoginAt; }
  public int getOnboardingVersion() { return onboardingVersion; }
  public LocalDateTime getOnboardingCompletedAt() { return onboardingCompletedAt; }
}
