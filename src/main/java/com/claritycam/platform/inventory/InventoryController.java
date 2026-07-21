package com.claritycam.platform.inventory;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final InventoryAssetRepository assets;
  private final StockItemRepository stock;

  public InventoryController(InventoryAssetRepository assets, StockItemRepository stock) {
    this.assets = assets;
    this.stock = stock;
  }

  @GetMapping("/assets")
  List<InventoryAsset> assets() {
    return assets.findAll();
  }

  @GetMapping("/stock")
  List<StockItem> stock() {
    return stock.findAll();
  }
}
