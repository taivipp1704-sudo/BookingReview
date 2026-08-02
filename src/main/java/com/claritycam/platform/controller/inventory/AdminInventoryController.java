package com.claritycam.platform.controller.inventory;

import com.claritycam.platform.model.inventory.InventoryAsset;
import com.claritycam.platform.model.inventory.InventoryLedgerEntry;
import com.claritycam.platform.model.inventory.StockItem;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
import com.claritycam.platform.service.inventory.InventoryLedgerService;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAREHOUSE','TECH')")
public class AdminInventoryController {
  private final InventoryAssetRepository assets;
  private final StockItemRepository stock;
  private final ProductRepository products;
  private final AuditService audit;
  private final InventoryLedgerService ledger;

  public AdminInventoryController(InventoryAssetRepository assets, StockItemRepository stock,
      ProductRepository products, AuditService audit, InventoryLedgerService ledger) {
    this.assets = assets;
    this.stock = stock;
    this.products = products;
    this.audit = audit;
    this.ledger = ledger;
  }

  @PostMapping("/assets")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  InventoryAsset createAsset(@Valid @RequestBody AssetCreateRequest request, Authentication authentication) {
    if (assets.existsById(request.serialId())) {
      throw ApiException.badRequest("Serial này đã tồn tại.");
    }
    requireProduct(request.productId());
    InventoryAsset asset = assets.save(new InventoryAsset(request.serialId().trim(), request.productId().trim(),
        request.status(), 0, LocalDate.now(), 0));
    ledger.append(null, asset.getProductId(), asset.getSerialId(), "ASSET_RECEIPT", 1, null,
        "Nhập serial mới", authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_ASSET_CREATED", "ASSET", asset.getSerialId(), asset.getProductId());
    return asset;
  }

  @PatchMapping("/assets/{serialId}/status")
  @Transactional
  InventoryAsset updateAssetStatus(@PathVariable String serialId, @Valid @RequestBody AssetStatusRequest request,
      Authentication authentication) {
    InventoryAsset asset = assets.findById(serialId).orElseThrow(() -> ApiException.notFound("Không tìm thấy serial thiết bị."));
    String previousStatus = asset.getStatus();
    asset.updateStatus(request.status());
    InventoryAsset saved = assets.save(asset);
    ledger.append(null, saved.getProductId(), saved.getSerialId(), "ASSET_STATUS_CHANGED", 0, null,
        previousStatus + " -> " + request.status(), authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_ASSET_STATUS_UPDATED", "ASSET", saved.getSerialId(), request.status());
    return saved;
  }

  @DeleteMapping("/assets/{serialId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void deleteAsset(@PathVariable String serialId, Authentication authentication) {
    InventoryAsset asset = assets.findById(serialId).orElseThrow(() -> ApiException.notFound("Không tìm thấy serial thiết bị."));
    if ("IN_USE".equals(asset.getStatus())) throw ApiException.badRequest("Không thể xóa thiết bị đang được thuê.");
    asset.updateStatus("RETIRED");
    assets.save(asset);
    ledger.append(null, asset.getProductId(), asset.getSerialId(), "ASSET_RETIRED", -1, null,
        "Lưu trữ serial, không xóa dữ liệu", authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_ASSET_RETIRED", "ASSET", serialId, asset.getProductId());
  }

  @PatchMapping("/stock/{productId}")
  @Transactional
  StockItem updateStock(@PathVariable String productId, @Valid @RequestBody StockAdjustmentRequest request,
      Authentication authentication) {
    requireProduct(productId);
    StockItem item = stock.findById(productId).orElseGet(() -> new StockItem(productId, 0, 0));
    if (request.totalQty() < item.getInUseQty()) {
      throw ApiException.badRequest("Tổng tồn kho không thể thấp hơn số lượng đang được thuê.");
    }
    int delta = request.totalQty() - item.getTotalQty();
    item.updateTotalQty(request.totalQty());
    StockItem saved = stock.save(item);
    ledger.append(null, saved.getProductId(), null, "STOCK_ADJUSTMENT", delta, saved.getTotalQty(),
        request.reason(), authentication.getName());
    audit.record(authentication.getName(), "INVENTORY_STOCK_UPDATED", "STOCK", saved.getProductId(), request.reason());
    return saved;
  }

  @GetMapping("/ledger")
  List<InventoryLedgerEntry> ledger() {
    return ledger.recent();
  }

  private void requireProduct(String productId) {
    if (!products.existsById(productId)) {
      throw ApiException.notFound("Thiết bị không tồn tại trong danh mục.");
    }
  }

  public record AssetCreateRequest(
      @NotBlank @Size(max = 96) String serialId,
      @NotBlank @Size(max = 96) String productId,
      @NotBlank @Pattern(regexp = "AVAILABLE|IN_USE|REPAIR|RETIRED") String status) {}

  public record AssetStatusRequest(
      @NotBlank @Pattern(regexp = "AVAILABLE|IN_USE|REPAIR|RETIRED") String status) {}

  public record StockAdjustmentRequest(@NotNull @Min(0) Integer totalQty,
                                       @NotBlank @Size(max = 300) String reason) {}
}
