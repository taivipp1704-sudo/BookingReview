package com.claritycam.platform.catalog;

import com.claritycam.platform.inventory.InventoryAssetRepository;
import com.claritycam.platform.inventory.StockItemRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
  private final ProductRepository products;
  private final BundleRepository bundles;
  private final InventoryAssetRepository assets;
  private final StockItemRepository stock;
  private final ProductBookingCountService bookingCounts;

  public CatalogController(ProductRepository products, BundleRepository bundles, InventoryAssetRepository assets,
      StockItemRepository stock, ProductBookingCountService bookingCounts) {
    this.products = products;
    this.bundles = bundles;
    this.assets = assets;
    this.stock = stock;
    this.bookingCounts = bookingCounts;
  }

  @GetMapping("/products")
  List<Product> products() {
    return bookingCounts.includeBookingCounts(products.findByActiveTrueOrderByLevelCodeAscNameAsc());
  }

  @GetMapping("/bundles")
  List<PublicBundle> bundles() {
    return bundles.findByActiveTrueOrderByNameAsc().stream().map(bundle -> new PublicBundle(
        bundle.getId(), bundle.getName(), bundle.getHourlyPrice(), bundle.getDailyPrice(),
        bundle.getMultiDayPrice(), bundle.getMultiDayDays(), bundle.isActive(), bundle.getImageUrl(),
        bundle.getDetailImageUrl(), bundle.getCurrentVersion(), bundle.getItems().stream()
            .map(item -> new PublicBundleItem(item.getProductId(), item.getQuantity())).toList())).toList();
  }

  @GetMapping("/availability")
  List<ProductAvailability> availability() {
    return products.findByActiveTrueOrderByLevelCodeAscNameAsc().stream().map(product -> {
      if (!"SERIALIZED".equals(product.getTrackingMode())) {
        return stock.findById(product.getId())
            .map(item -> new ProductAvailability(product.getId(), item.getTotalQty(), item.getAvailableQty()))
            .orElseGet(() -> new ProductAvailability(product.getId(), 0, 0));
      }
      long total = assets.countByProductId(product.getId());
      long available = assets.countByProductIdAndStatus(product.getId(), "AVAILABLE");
      return new ProductAvailability(product.getId(), total, available);
    }).toList();
  }

  public record ProductAvailability(String productId, long totalQty, long availableQty) {}
  public record PublicBundle(String id, String name, BigDecimal hourlyPrice, BigDecimal dailyPrice,
                             BigDecimal multiDayPrice, int multiDayDays, boolean active, String imageUrl,
                             String detailImageUrl, int currentVersion, List<PublicBundleItem> items) {}
  public record PublicBundleItem(String productId, int quantity) {}
}
