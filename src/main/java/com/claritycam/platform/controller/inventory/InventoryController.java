package com.claritycam.platform.controller.inventory;

import com.claritycam.platform.model.inventory.InventoryAsset;
import com.claritycam.platform.model.inventory.StockItem;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
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
