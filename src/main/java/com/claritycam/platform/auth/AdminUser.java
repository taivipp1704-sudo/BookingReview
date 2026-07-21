package com.claritycam.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_users")
public class AdminUser {
  @Id
  private String id;

  @Column(unique = true, nullable = false)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String role;

  private boolean active;

  protected AdminUser() {}

  public AdminUser(String id, String email, String passwordHash, String role, boolean active) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.active = active;
  }

  public void update(String role, boolean active, String passwordHash) {
    this.role = role;
    this.active = active;
    if (passwordHash != null && !passwordHash.isBlank()) this.passwordHash = passwordHash;
  }

  public String getId() { return id; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getRole() { return role; }
  public boolean isActive() { return active; }
}
