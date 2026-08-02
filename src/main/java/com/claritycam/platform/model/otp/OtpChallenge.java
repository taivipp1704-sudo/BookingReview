package com.claritycam.platform.model.otp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {
  @Id
  private String id;

  @Column(nullable = false, length = 64)
  private String phoneHash;

  @Column(nullable = false, length = 255)
  private String codeHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
  private OtpPurpose purpose;

  private LocalDateTime createdAt;
  private LocalDateTime expiresAt;
  private int attempts;
  private LocalDateTime verifiedAt;
  private LocalDateTime consumedAt;

  @Column(length = 255)
  private String verificationTokenHash;

  protected OtpChallenge() {}

  public OtpChallenge(String id, String phoneHash, String codeHash, OtpPurpose purpose, LocalDateTime expiresAt) {
    this.id = id;
    this.phoneHash = phoneHash;
    this.codeHash = codeHash;
    this.purpose = purpose;
    this.createdAt = LocalDateTime.now();
    this.expiresAt = expiresAt;
  }

  public void incrementAttempts() { attempts += 1; }

  public void verify(String verificationTokenHash) {
    this.verifiedAt = LocalDateTime.now();
    this.verificationTokenHash = verificationTokenHash;
  }

  public void consume() { this.consumedAt = LocalDateTime.now(); }

  public boolean isExpired() { return !expiresAt.isAfter(LocalDateTime.now()); }
  public String getId() { return id; }
  public String getPhoneHash() { return phoneHash; }
  public String getCodeHash() { return codeHash; }
  public OtpPurpose getPurpose() { return purpose; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public int getAttempts() { return attempts; }
  public LocalDateTime getVerifiedAt() { return verifiedAt; }
  public LocalDateTime getConsumedAt() { return consumedAt; }
  public String getVerificationTokenHash() { return verificationTokenHash; }
}
