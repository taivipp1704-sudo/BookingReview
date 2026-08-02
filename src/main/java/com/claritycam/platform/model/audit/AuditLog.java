package com.claritycam.platform.model.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String actor;
  private String action;
  private String targetType;
  private String targetId;
  private String note;
  private LocalDateTime createdAt;

  protected AuditLog() {}

  public AuditLog(String actor, String action, String targetType, String targetId, String note) {
    this.actor = actor;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.note = note;
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() { return id; }
  public String getActor() { return actor; }
  public String getAction() { return action; }
  public String getTargetType() { return targetType; }
  public String getTargetId() { return targetId; }
  public String getNote() { return note; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
