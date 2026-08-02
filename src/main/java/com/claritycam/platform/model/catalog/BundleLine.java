package com.claritycam.platform.model.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bundle_lines")
public class BundleLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String productId;
  private int quantity;

  protected BundleLine() {}

  public BundleLine(String productId, int quantity) {
    this.productId = productId;
    this.quantity = quantity;
  }

  public Long getId() { return id; }
  public String getProductId() { return productId; }
  public int getQuantity() { return quantity; }
}
