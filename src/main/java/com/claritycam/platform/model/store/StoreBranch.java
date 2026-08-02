package com.claritycam.platform.model.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_branches")
public class StoreBranch {
  @Id
  private String id;

  @Column(nullable = false, unique = true, length = 32)
  private String code;

  @Column(nullable = false, length = 180)
  private String name;

  @Column(nullable = false, length = 500)
  private String address;

  @Column(length = 30)
  private String phone;

  @Column(length = 500)
  private String note;

  private boolean active;
  private int sortOrder;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  protected StoreBranch() {}

  public StoreBranch(String id, String code, String name, String address, String phone, String note,
      boolean active, int sortOrder) {
    this.id = id;
    this.code = code;
    this.createdAt = LocalDateTime.now();
    apply(name, address, phone, note, active, sortOrder);
  }

  public void apply(String name, String address, String phone, String note, boolean active, int sortOrder) {
    this.name = name.trim();
    this.address = address.trim();
    this.phone = normalize(phone);
    this.note = normalize(note);
    this.active = active;
    this.sortOrder = Math.max(0, sortOrder);
    this.updatedAt = LocalDateTime.now();
  }

  public void deactivate() {
    this.active = false;
    this.updatedAt = LocalDateTime.now();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  public String getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getAddress() { return address; }
  public String getPhone() { return phone; }
  public String getNote() { return note; }
  public boolean isActive() { return active; }
  public int getSortOrder() { return sortOrder; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
