package com.claritycam.platform.model.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_assets")
public class InventoryAsset {
  @Id
  private String serialId;
  private String productId;
  private String status;
  private int usageCount;
  private LocalDate lastCheck;
  private int batteryCycles;

  protected InventoryAsset() {}

  public InventoryAsset(String serialId, String productId, String status, int usageCount, LocalDate lastCheck, int batteryCycles) {
    this.serialId = serialId;
    this.productId = productId;
    this.status = status;
    this.usageCount = usageCount;
    this.lastCheck = lastCheck;
    this.batteryCycles = batteryCycles;
  }

  public void updateStatus(String status) {
    this.status = status;
    this.lastCheck = LocalDate.now();
  }

  public String getSerialId() { return serialId; }
  public String getProductId() { return productId; }
  public String getStatus() { return status; }
  public int getUsageCount() { return usageCount; }
  public LocalDate getLastCheck() { return lastCheck; }
  public int getBatteryCycles() { return batteryCycles; }
}
