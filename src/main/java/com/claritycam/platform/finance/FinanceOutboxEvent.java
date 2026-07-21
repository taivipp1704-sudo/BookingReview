package com.claritycam.platform.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "finance_outbox_events")
public class FinanceOutboxEvent {
  @Id private String id;
  private String aggregateType;
  private String aggregateId;
  private String eventType;
  private String correlationId;
  @Column(length = 2000) private String payload;
  private String status;
  private int attempts;
  private LocalDateTime createdAt;
  private LocalDateTime publishedAt;

  protected FinanceOutboxEvent() {}
  public FinanceOutboxEvent(String aggregateType, String aggregateId, String eventType, String correlationId,
      String payload) {
    this.id = "EVT-" + CommercialSnapshotLine.compactId();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.correlationId = correlationId;
    this.payload = payload;
    this.status = "PENDING";
    this.createdAt = LocalDateTime.now();
  }
  public String getId() { return id; }
  public String getAggregateType() { return aggregateType; }
  public String getAggregateId() { return aggregateId; }
  public String getEventType() { return eventType; }
  public String getCorrelationId() { return correlationId; }
  public String getPayload() { return payload; }
  public String getStatus() { return status; }
  public int getAttempts() { return attempts; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
}
