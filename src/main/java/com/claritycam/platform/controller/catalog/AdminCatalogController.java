package com.claritycam.platform.controller.catalog;

import com.claritycam.platform.dto.catalog.ProductCreatePayload;
import com.claritycam.platform.dto.catalog.ProductPayload;
import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
import com.claritycam.platform.repository.store.StoreBranchRepository;
import com.claritycam.platform.service.catalog.ProductBookingCountService;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.inventory.InventoryAsset;
import com.claritycam.platform.service.inventory.InventoryLedgerService;
import com.claritycam.platform.model.inventory.StockItem;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {
  private static final Set<String> LEGACY_MOCK_PRODUCT_IDS = Set.of(
      "GEAR-001", "GEAR-002", "GEAR-003", "GEAR-004",
      "ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005", "ACC-006", "ACC-007");

  private final ProductRepository products;
  private final AuditService audit;
  private final ProductBookingCountService bookingCounts;
  private final InventoryAssetRepository assets;
  private final StockItemRepository stock;
  private final InventoryLedgerService ledger;
  private final StoreBranchRepository storeBranches;

  public AdminCatalogController(ProductRepository products, AuditService audit,
      ProductBookingCountService bookingCounts, InventoryAssetRepository assets,
      StockItemRepository stock, InventoryLedgerService ledger, StoreBranchRepository storeBranches) {
    this.products = products;
    this.audit = audit;
    this.bookingCounts = bookingCounts;
    this.assets = assets;
    this.stock = stock;
    this.ledger = ledger;
    this.storeBranches = storeBranches;
  }

  @GetMapping("/products")
  List<Product> products() {
    List<Product> sorted = products.findAll().stream()
        .filter(product -> !LEGACY_MOCK_PRODUCT_IDS.contains(product.getId()))
        .sorted(Comparator.comparing(Product::getLevelCode).thenComparing(Product::getName))
        .toList();
    return bookingCounts.includeBookingCounts(sorted);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Product create(@Valid @RequestBody ProductPayload payload, Authentication authentication) {
    validateStoreBranch(payload.storeBranchId());
    String productId = productId(null, payload);
    Product product = products.save(new Product(productId, payload));
    audit.record(authentication.getName(), "CATALOG_PRODUCT_CREATED", "PRODUCT", product.getId(), product.getName());
    return product;
  }

  @PostMapping("/products/with-inventory")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @Transactional
  Product createWithInventory(@Valid @RequestBody ProductCreatePayload request, Authentication authentication) {
    ProductPayload payload = request.product();
    validateStoreBranch(payload.storeBranchId());
    String productId = productId(request.productCode(), payload);
    if (products.existsById(productId)) {
      throw ApiException.badRequest("Mã sản phẩm đã tồn tại.");
    }

    List<String> serialNumbers = normalizedSerials(request.serialNumbers());
    boolean serialized = "SERIALIZED".equals(payload.trackingMode());
    int initialStockQty = request.initialStockQty() == null ? 0 : request.initialStockQty();
    if (serialized && initialStockQty > 0) {
      throw ApiException.badRequest("Thiết bị theo serial phải nhập danh sách serial thay vì tổng số lượng.");
    }
    if (!serialized && !serialNumbers.isEmpty()) {
      throw ApiException.badRequest("Sản phẩm theo số lượng không sử dụng danh sách serial.");
    }
    for (String serialNumber : serialNumbers) {
      if (assets.existsById(serialNumber)) {
        throw ApiException.badRequest("Serial đã tồn tại: " + serialNumber);
      }
    }

    Product product = products.save(new Product(productId, payload));
    if (serialized) {
      List<InventoryAsset> createdAssets = serialNumbers.stream()
          .map(serialNumber -> new InventoryAsset(serialNumber, productId, "AVAILABLE", 0, LocalDate.now(), 0))
          .toList();
      assets.saveAll(createdAssets);
      createdAssets.forEach(asset -> ledger.append(null, productId, asset.getSerialId(), "ASSET_RECEIPT", 1,
          null, "Khởi tạo tồn kho cùng sản phẩm", authentication.getName()));
    } else {
      stock.save(new StockItem(productId, initialStockQty, 0));
      if (initialStockQty > 0) {
        ledger.append(null, productId, null, "INITIAL_STOCK", initialStockQty, initialStockQty,
            "Khởi tạo tồn kho cùng sản phẩm", authentication.getName());
      }
    }
    audit.record(authentication.getName(), "CATALOG_PRODUCT_CREATED", "PRODUCT", product.getId(), product.getName());
    return product;
  }

  @PatchMapping("/products/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Product update(@PathVariable String id, @Valid @RequestBody ProductPayload payload, Authentication authentication) {
    validateStoreBranch(payload.storeBranchId());
    Product product = products.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị."));
    product.apply(payload);
    Product saved = products.save(product);
    audit.record(authentication.getName(), "CATALOG_PRODUCT_UPDATED", "PRODUCT", saved.getId(), saved.getName());
    return saved;
  }

  @DeleteMapping("/products/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Product archive(@PathVariable String id, Authentication authentication) {
    Product product = products.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị."));
    product.deactivate();
    Product saved = products.save(product);
    audit.record(authentication.getName(), "CATALOG_PRODUCT_ARCHIVED", "PRODUCT", id, saved.getName());
    return saved;
  }

  private String productId(String requestedCode, ProductPayload payload) {
    if (requestedCode != null && !requestedCode.isBlank()) {
      return requestedCode.trim().toUpperCase(Locale.ROOT);
    }
    String prefix = "L1".equalsIgnoreCase(payload.levelCode()) ? "GEAR-" : "ACC-";
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private List<String> normalizedSerials(List<String> serialNumbers) {
    if (serialNumbers == null) return List.of();
    List<String> normalized = serialNumbers.stream()
        .map(value -> value.trim().toUpperCase(Locale.ROOT))
        .filter(value -> !value.isBlank())
        .toList();
    if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
      throw ApiException.badRequest("Danh sách serial có giá trị trùng nhau.");
    }
    return normalized;
  }

  private void validateStoreBranch(String storeBranchId) {
    if (storeBranchId == null || storeBranchId.isBlank()) return;
    if (!storeBranches.existsById(storeBranchId.trim())) {
      throw ApiException.badRequest("Chi nhánh được chọn không tồn tại.");
    }
  }
}
