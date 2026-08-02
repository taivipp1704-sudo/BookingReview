package com.claritycam.platform.controller.catalog;

import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.repository.catalog.BundleRepository;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
import com.claritycam.platform.service.catalog.ProductBookingCountService;
import com.claritycam.platform.service.common.ClientAddressResolver;
import com.claritycam.platform.service.common.RateLimitService;
import com.claritycam.platform.service.customer.CustomerSessionAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Duration;
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
  private final CustomerSessionAccessService customerAccess;
  private final RateLimitService rateLimit;
  private final ClientAddressResolver clientAddressResolver;

  public CatalogController(ProductRepository products, BundleRepository bundles, InventoryAssetRepository assets,
      StockItemRepository stock, ProductBookingCountService bookingCounts,
      CustomerSessionAccessService customerAccess, RateLimitService rateLimit,
      ClientAddressResolver clientAddressResolver) {
    this.products = products;
    this.bundles = bundles;
    this.assets = assets;
    this.stock = stock;
    this.bookingCounts = bookingCounts;
    this.customerAccess = customerAccess;
    this.rateLimit = rateLimit;
    this.clientAddressResolver = clientAddressResolver;
  }

  @GetMapping("/products")
  List<Product> products(HttpServletRequest request) {
    requirePreviewAccess(request);
    return bookingCounts.includeBookingCounts(products.findByActiveTrueOrderByLevelCodeAscNameAsc());
  }

  @GetMapping("/bundles")
  List<PublicBundle> bundles(HttpServletRequest request) {
    requirePreviewAccess(request);
    return bundles.findByActiveTrueOrderByNameAsc().stream().map(bundle -> new PublicBundle(
        bundle.getId(), bundle.getName(), bundle.getHourlyPrice(), bundle.getDailyPrice(),
        bundle.getMultiDayPrice(), bundle.getMultiDayDays(), bundle.isActive(), bundle.getImageUrl(),
        bundle.getDetailImageUrl(), bundle.getCurrentVersion(), bundle.getItems().stream()
            .map(item -> new PublicBundleItem(item.getProductId(), item.getQuantity())).toList())).toList();
  }

  @GetMapping("/availability")
  List<ProductAvailability> availability(HttpServletRequest request) {
    requirePreviewAccess(request);
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

  private void requirePreviewAccess(HttpServletRequest request) {
    customerAccess.require(request);
    rateLimit.check("catalog:ip:" + clientAddressResolver.resolve(request), 240, Duration.ofMinutes(1));
  }

  public record ProductAvailability(String productId, long totalQty, long availableQty) {}
  public record PublicBundle(String id, String name, BigDecimal hourlyPrice, BigDecimal dailyPrice,
                             BigDecimal multiDayPrice, int multiDayDays, boolean active, String imageUrl,
                             String detailImageUrl, int currentVersion, List<PublicBundleItem> items) {}
  public record PublicBundleItem(String productId, int quantity) {}
}
