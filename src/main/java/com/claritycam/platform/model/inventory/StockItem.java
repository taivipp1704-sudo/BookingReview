package com.claritycam.platform.model.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_items")
public class StockItem {
  @Id
  private String productId;
  private int totalQty;
  private int inUseQty;

  protected StockItem() {}

  public StockItem(String productId, int totalQty, int inUseQty) {
    this.productId = productId;
    this.totalQty = totalQty;
    this.inUseQty = inUseQty;
  }

  public void updateTotalQty(int totalQty) {
    this.totalQty = totalQty;
  }

  public void adjustInUse(int delta) {
    int next = inUseQty + delta;
    if (next < 0 || next > totalQty) {
      throw new IllegalArgumentException("Số lượng đang thuê không hợp lệ.");
    }
    this.inUseQty = next;
  }

  public String getProductId() { return productId; }
  public int getTotalQty() { return totalQty; }
  public int getInUseQty() { return inUseQty; }
  public int getAvailableQty() { return totalQty - inUseQty; }
}
