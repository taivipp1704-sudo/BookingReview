package com.claritycam.platform.model.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_ledger_entries")
public class InventoryLedgerEntry {
  @Id
  private String id;
  private String documentId;
  private String productId;
  private String serialId;
  private String movementType;
  private int quantityDelta;
  private Integer balanceAfter;
  @Column(length = 500)
  private String reason;
  @Column(length = 180)
  private String actor;
  private LocalDateTime createdAt;

  protected InventoryLedgerEntry() {}

  public InventoryLedgerEntry(String documentId, String productId, String serialId, String movementType,
      int quantityDelta, Integer balanceAfter, String reason, String actor) {
    this.id = "LED-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    this.documentId = documentId;
    this.productId = productId;
    this.serialId = serialId;
    this.movementType = movementType;
    this.quantityDelta = quantityDelta;
    this.balanceAfter = balanceAfter;
    this.reason = reason == null ? "" : reason.trim();
    this.actor = actor;
    this.createdAt = LocalDateTime.now();
  }

  public String getId() { return id; }
  public String getDocumentId() { return documentId; }
  public String getProductId() { return productId; }
  public String getSerialId() { return serialId; }
  public String getMovementType() { return movementType; }
  public int getQuantityDelta() { return quantityDelta; }
  public Integer getBalanceAfter() { return balanceAfter; }
  public String getReason() { return reason; }
  public String getActor() { return actor; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
