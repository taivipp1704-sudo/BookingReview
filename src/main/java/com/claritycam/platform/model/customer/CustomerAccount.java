package com.claritycam.platform.model.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_accounts")
public class CustomerAccount {
  /** Sau ngần này lần nhập sai PIN liên tiếp, PIN bị vô hiệu hoá và khách phải
   * đăng nhập lại bằng mật khẩu đầy đủ rồi đặt PIN mới (bảo vệ không gian mã 6 số
   * nhỏ trước tấn công dò mã, bổ sung cho giới hạn tần suất ở RateLimitService). */
  public static final int PIN_MAX_FAILED_ATTEMPTS = 5;

  @Id private String id;
  @Column(unique = true, nullable = false) private String phoneNormalized;
  @Column(nullable = false) private String name;
  @Column(unique = true, length = 255) private String email;
  @Column(length = 100) private String passwordHash;
  @Column(length = 100) private String pinHash;
  @Column(nullable = false) private int pinFailedAttempts;
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

  /** Đặt hoặc đổi PIN đăng nhập nhanh. Không đụng đến mật khẩu đầy đủ — cả hai
   * cùng tồn tại song song, khách dùng cái nào tuỳ ý. */
  public void setPinHash(String pinHash) {
    this.pinHash = pinHash;
    this.pinFailedAttempts = 0;
  }

  public void disablePin() {
    this.pinHash = null;
    this.pinFailedAttempts = 0;
  }

  public void registerPinSuccess() {
    this.pinFailedAttempts = 0;
  }

  /** @return true nếu PIN vừa bị vô hiệu hoá do nhập sai quá số lần cho phép. */
  public boolean registerPinFailure() {
    this.pinFailedAttempts++;
    if (this.pinFailedAttempts >= PIN_MAX_FAILED_ATTEMPTS) {
      disablePin();
      return true;
    }
    return false;
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
  public String getPinHash() { return pinHash; }
  public boolean hasPin() { return pinHash != null; }
  public boolean isActive() { return active; }
  public boolean isMustChangePassword() { return mustChangePassword; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getLastLoginAt() { return lastLoginAt; }
  public int getOnboardingVersion() { return onboardingVersion; }
  public LocalDateTime getOnboardingCompletedAt() { return onboardingCompletedAt; }
}
